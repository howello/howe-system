package com.howe.meal.tool.operation;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.operation.ToolOperationStepContext;
import com.howe.common.tool.operation.ToolOperationStepHandler;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.service.IMealProposalService;
import com.howe.meal.service.MealProposalAuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 提案自动审核的持久化步骤处理器。
 *
 * <p>只执行父操作登记的固定代码键步骤。执行前由平台重新加载账号与原授权，
 * 处理器先复核提案仍存在、归属当前家庭、版本一致且仍待审核，再调用自动审核；
 * 撤权或提案被人工改判时终止，不删除已提交提案。</p>
 */
public final class MealProposalAutoAuditStepHandler implements ToolOperationStepHandler {

    /** 步骤代码键。 */
    public static final String STEP_KEY = MealProposalOperationHandler.AUTO_AUDIT_STEP;

    private final IMealProposalService proposals;

    private final MealProposalAuditService audits;

    /**
     * 构造自动审核步骤处理器。
     *
     * @param proposals 提案服务
     * @param audits 自动审核服务
     */
    public MealProposalAutoAuditStepHandler(IMealProposalService proposals, MealProposalAuditService audits) {
        this.proposals = Objects.requireNonNull(proposals);
        this.audits = Objects.requireNonNull(audits);
    }

    @Override
    public String stepKey() {
        return STEP_KEY;
    }

    @Override
    public String stepVersion() {
        return "1";
    }

    /** 复核提案仍是待审核且版本一致后执行自动审核。 */
    @Override
    public ToolOperationStepContext.StepOutcome execute(ToolOperationStepContext context, Map<String, Object> payload) {
        String proposalIdText = payload == null ? null : String.valueOf(payload.get("proposalId"));
        if (proposalIdText == null || !proposalIdText.matches("[1-9][0-9]{0,18}")) {
            return ToolOperationStepContext.StepOutcome.notApplied("invalid_step_payload");
        }
        Long proposalId = Long.parseLong(proposalIdText);
        MealProposal proposal;
        try {
            // 每次执行都重新读取提案，不复用父操作时的快照。
            proposal = proposals.selectMealProposalDetail(proposalId);
        } catch (ServiceException exception) {
            return ToolOperationStepContext.StepOutcome.aborted("proposal_unavailable");
        }
        String submittedVersion = payload.get("submittedVersion") == null ? null
                : String.valueOf(payload.get("submittedVersion"));
        if (submittedVersion == null) {
            return ToolOperationStepContext.StepOutcome.notApplied("invalid_step_payload");
        }
        if (proposal.getDataVersion() == null || proposal.getDataVersion() != Long.parseLong(submittedVersion)) {
            return ToolOperationStepContext.StepOutcome.aborted("proposal_changed");
        }
        if (!MealProposal.STATUS_PENDING.equals(proposal.getStatus())) {
            return ToolOperationStepContext.StepOutcome.aborted("proposal_already_audited");
        }
        context.guard().check();
        com.howe.meal.domain.dto.DishAiAuditResult audit = audits.prepareAuditForTool(proposal, context.guard());
        return context.guard().commitAuthorized(() -> {
            MealProposalAuditService.AuditRun run = audits.applyAuditForTool(proposal, audit);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proposalId", String.valueOf(proposalId));
            result.put("auditState", run.outcome().name().toLowerCase(java.util.Locale.ROOT));
            if (run.dishId() != null) {
                result.put("dishId", String.valueOf(run.dishId()));
            }
            return switch (run.outcome()) {
                case APPROVED, REJECTED -> ToolOperationStepContext.StepOutcome.applied(result);
                case CONFLICT -> ToolOperationStepContext.StepOutcome.aborted("proposal_changed");
                case FAILED -> ToolOperationStepContext.StepOutcome.notApplied("audit_failed");
            };
        });
    }
}
