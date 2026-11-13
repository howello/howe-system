package com.howe.meal.tool.operation;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationContinuation;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.meal.tool.service.IMealExecutionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 新菜提案写操作处理器。
 *
 * <p>提交工具只创建提案，并在父操作成功事务内登记固定代码键
 * {@value #AUTO_AUDIT_STEP} 的自动审核步骤；AI 调用与菜品上架由平台在真实执行上下文中
 * 复核授权后执行。人工审核工具保留原有改判语义并联动菜品上下架。</p>
 */
public final class MealProposalOperationHandler implements ToolOperationHandler {

    /** 提交工具名。 */
    private static final String SUBMIT = "meal_proposal_submit";

    /** 审核工具名。 */
    private static final String AUDIT = "meal_proposal_audit";

    /** 自动审核步骤代码键。 */
    public static final String AUTO_AUDIT_STEP = "meal.proposal.auto-audit";

    private final String toolName;

    private final IMealExecutionService executions;

    /**
     * 构造提案写处理器。
     *
     * @param toolName 工具名
     * @param executions 领域执行服务
     */
    public MealProposalOperationHandler(String toolName, IMealExecutionService executions) {
        this.toolName = Objects.requireNonNull(toolName);
        this.executions = Objects.requireNonNull(executions);
    }

    @Override
    public Key key() {
        return new Key(toolName, "1");
    }

    /** 规范化提案计划。 */
    @Override
    public Plan plan(ToolOperationContext context, Map<String, Object> arguments) {
        return SUBMIT.equals(toolName) ? submitPlan(arguments) : auditPlan(arguments);
    }

    /** 重新确认提案仍可见且版本一致。 */
    @Override
    public void revalidate(ToolOperationContext context, Plan plan) {
        if (SUBMIT.equals(toolName)) {
            return;
        }
        IMealExecutionService.ProposalSnapshot snapshot = executions.proposalSnapshot(Long.parseLong(plan.targetId()));
        if (context.phase() == ToolOperationContext.Phase.RESULT_READ) {
            return;
        }
        long expected = Long.parseLong(plan.expectedVersion());
        if (snapshot.version() == null || snapshot.version() != expected) {
            throw new ToolException("version_conflict", "提案已变化，请重新预览");
        }
        String expectedStatus = String.valueOf(plan.arguments().get("expectedStatus"));
        String actual = statusName(snapshot.status());
        if (!expectedStatus.equals(actual)) {
            throw new ToolException("version_conflict", "提案状态已变化，请重新预览");
        }
    }

    /** 确认事务不做额外占用。 */
    @Override
    public void reserve(ToolOperationContext context, Plan plan) {
        revalidate(context, plan);
    }

    /** 执行提案写操作。 */
    @Override
    public Outcome execute(ToolOperationContext context, Plan plan) {
        try {
            if (SUBMIT.equals(toolName)) {
                IMealExecutionService.ProposalSubmitResult result = executions.submitProposal(submitCommand(plan));
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("proposalId", result.proposalId());
                evidence.put("version", result.version() == null ? null : String.valueOf(result.version()));
                evidence.put("auditStep", AUTO_AUDIT_STEP);
                return new Outcome(OutcomeKind.APPLIED, "LOCAL", "submitted", evidence);
            }
            IMealExecutionService.ProposalAuditResult result = executions.auditProposal(
                    Long.parseLong(plan.targetId()), Long.parseLong(plan.expectedVersion()),
                    String.valueOf(plan.arguments().get("expectedStatus")),
                    String.valueOf(plan.arguments().get("decision")),
                    plan.arguments().get("reason") == null ? null : String.valueOf(plan.arguments().get("reason")));
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("proposalId", result.proposalId());
            evidence.put("status", statusName(result.status()));
            evidence.put("version", result.version() == null ? null : String.valueOf(result.version()));
            evidence.put("dishId", result.dishId());
            evidence.put("dishEffect", result.dishEffect());
            return new Outcome(OutcomeKind.APPLIED, "LOCAL", "audited", evidence);
        } catch (ServiceException exception) {
            throw translate(exception);
        }
    }

    /** 本地写不涉及远端对账。 */
    @Override
    public Outcome reconcile(ToolOperationContext context, Plan plan) {
        return new Outcome(OutcomeKind.UNKNOWN, "RECONCILE", "reconcile_not_supported", Map.of());
    }

    /** 安全结果。 */
    @Override
    public Map<String, Object> complete(ToolOperationContext context, Plan plan, Outcome outcome) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("proposalId", outcome.evidence().get("proposalId"));
        if (SUBMIT.equals(toolName)) {
            result.put("submissionState", "submitted");
            result.put("version", outcome.evidence().get("version"));
            result.put("auditStep", outcome.evidence().get("auditStep"));
            result.put("auditState", "QUEUED");
            result.put("notice", "提案已提交，AI 自动审核将随后执行；通过时会自动创建并上架菜品。");
            return result;
        }
        result.put("status", outcome.evidence().get("status"));
        result.put("version", outcome.evidence().get("version"));
        result.put("dishId", outcome.evidence().get("dishId"));
        result.put("dishEffect", outcome.evidence().get("dishEffect"));
        return result;
    }

    /** 声明提交后必须执行的自动审核步骤。 */
    @Override
    public List<ToolOperationContinuation> continuations(ToolOperationContext context, Plan plan, Outcome outcome) {
        if (!SUBMIT.equals(toolName) || outcome == null || outcome.kind() != OutcomeKind.APPLIED) {
            return List.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("proposalId", String.valueOf(outcome.evidence().get("proposalId")));
        payload.put("submittedVersion", String.valueOf(outcome.evidence().get("version")));
        return List.of(new ToolOperationContinuation(AUTO_AUDIT_STEP, payload));
    }

    /** 本地事务回滚即未生效。 */
    @Override
    public void releaseUnapplied(ToolOperationContext context, Plan plan, String reasonCode) {
        // 无额外占用。
    }

    /** 提交计划。 */
    private Plan submitPlan(Map<String, Object> arguments) {
        String name = String.valueOf(arguments.get("name"));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", name);
        plan.put("description", arguments.get("description"));
        plan.put("reason", arguments.get("reason"));
        plan.put("notes", arguments.get("notes"));
        plan.put("categoryId", arguments.get("categoryId"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", name);
        summary.put("categoryId", arguments.get("categoryId"));
        summary.put("descriptionChars", length(arguments.get("description")));
        summary.put("notesChars", length(arguments.get("notes")));
        // 确认页必须原文展示自动审核与上架后果。
        summary.put("effect", "提交后可能由 AI 自动审核；通过时会自动创建并上架菜品，驳回时不会创建。");
        summary.put("aiNotice", "提案内容会发送给现有 AI 服务用于审核，可能生成封面并产生现有服务费用。");
        return new Plan(plan, summary, "meal_proposal", null, null, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.MEAL_WRITE);
    }

    /** 审核计划。 */
    private Plan auditPlan(Map<String, Object> arguments) {
        String proposalId = String.valueOf(arguments.get("proposalId"));
        String version = String.valueOf(arguments.get("expectedVersion"));
        String expectedStatus = String.valueOf(arguments.get("expectedStatus"));
        String decision = String.valueOf(arguments.get("decision"));
        IMealExecutionService.ProposalSnapshot snapshot = executions.proposalSnapshot(Long.parseLong(proposalId));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("proposalId", proposalId);
        plan.put("expectedVersion", version);
        plan.put("expectedStatus", expectedStatus);
        plan.put("decision", decision);
        plan.put("reason", arguments.get("reason"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("proposalId", proposalId);
        summary.put("expectedVersion", version);
        summary.put("expectedStatus", expectedStatus);
        summary.put("decision", decision);
        summary.put("dishId", snapshot.dishId());
        summary.put("effect", dishEffectText(snapshot, decision));
        return new Plan(plan, summary, "meal_proposal", proposalId, version, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.MEAL_WRITE);
    }

    /** 确认页后果文案。 */
    private static String dishEffectText(IMealExecutionService.ProposalSnapshot snapshot, String decision) {
        boolean hasDish = snapshot.dishId() != null;
        if ("approved".equals(decision)) {
            return hasDish ? "重新上架已关联的菜品" : "将创建一条新菜品并上架";
        }
        return hasDish ? "将下架已关联的菜品" : "只驳回提案，不会创建菜品";
    }

    /** 计划转提交命令。 */
    private IMealExecutionService.ProposalSubmitCommand submitCommand(Plan plan) {
        Object categoryId = plan.arguments().get("categoryId");
        return new IMealExecutionService.ProposalSubmitCommand(String.valueOf(plan.arguments().get("name")),
                stringOrNull(plan.arguments().get("description")), stringOrNull(plan.arguments().get("reason")),
                stringOrNull(plan.arguments().get("notes")), null,
                categoryId == null ? null : Long.parseLong(String.valueOf(categoryId)));
    }

    /** 状态码转名称。 */
    private static String statusName(String status) {
        if ("1".equals(status)) {
            return "approved";
        }
        if ("2".equals(status)) {
            return "rejected";
        }
        return "pending";
    }

    /** 字符数。 */
    private static int length(Object value) {
        return value == null ? 0 : String.valueOf(value).length();
    }

    /** 可空字符串。 */
    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 业务异常收敛。 */
    private static ToolException translate(ServiceException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("已变化") || message.contains("请刷新")) {
            return new ToolException("version_conflict", "内容已发生变化，请重新预览");
        }
        if (message.contains("不存在") || message.contains("无权")) {
            return new ToolException("not_found_or_forbidden", "对象不存在或无权访问");
        }
        if (message.contains("只能") || message.contains("公共主类")) {
            return new ToolException("invalid_arguments", "提案参数不合法");
        }
        return new ToolException("state_conflict", "提案操作未完成");
    }
}
