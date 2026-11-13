package com.howe.meal.tool.operation;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.meal.tool.service.IMealExecutionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 点餐订单写操作处理器。
 *
 * <p>同一处理器按固定工具名注册五个实例：下单、取消、接单、拒单、完成。
 * 计划阶段只做参数规范化与快照校验，不产生任何写入；执行阶段复用领域服务的事务与守卫。</p>
 */
public final class MealOrderOperationHandler implements ToolOperationHandler {

    /** 下单工具名。 */
    private static final String SUBMIT = "meal_order_submit";

    /** 取消工具名。 */
    private static final String CANCEL = "meal_order_cancel";

    /** 接单工具名。 */
    private static final String ACCEPT = "meal_order_accept";

    /** 拒单工具名。 */
    private static final String REFUSE = "meal_order_refuse";

    /** 完成工具名。 */
    private static final String FINISH = "meal_order_finish";

    private final String toolName;

    private final IMealExecutionService executions;

    /**
     * 构造订单写处理器。
     *
     * @param toolName 工具名
     * @param executions 领域执行服务
     */
    public MealOrderOperationHandler(String toolName, IMealExecutionService executions) {
        this.toolName = Objects.requireNonNull(toolName);
        this.executions = Objects.requireNonNull(executions);
    }

    @Override
    public Key key() {
        return new Key(toolName, "1");
    }

    /** 规范化计划：只读取当前快照做可见性与版本提示。 */
    @Override
    public Plan plan(ToolOperationContext context, Map<String, Object> arguments) {
        if (SUBMIT.equals(toolName)) {
            return submitPlan(arguments);
        }
        if (CANCEL.equals(toolName) || FINISH.equals(toolName)) {
            return singleOrderPlan(arguments);
        }
        return candidatePlan(arguments);
    }

    /** 执行前重新确认对象仍可访问且版本未变化。 */
    @Override
    public void revalidate(ToolOperationContext context, Plan plan) {
        if (SUBMIT.equals(toolName)) {
            if (context.phase() != ToolOperationContext.Phase.RESULT_READ) {
                IMealExecutionService.OrderPreview current = executions.previewOrder(submitCommand(plan.arguments()));
                if (!Objects.equals(plan.configurationDigest(), previewDigest(current))) {
                    throw new ToolException("version_conflict", "下单内容已变化，请重新预览");
                }
            }
            return;
        }
        Long orderId = Long.parseLong(plan.targetId());
        IMealExecutionService.OrderSnapshot snapshot = executions.orderSnapshot(orderId);
        if (context.phase() == ToolOperationContext.Phase.RESULT_READ) {
            return;
        }
        long expected = Long.parseLong(plan.expectedVersion());
        if (snapshot.version() == null || snapshot.version() != expected) {
            throw new ToolException("version_conflict", "订单已变化，请重新预览");
        }
        if (ACCEPT.equals(toolName) || REFUSE.equals(toolName)) {
            IMealExecutionService.CandidateSnapshot candidate = executions.candidateSnapshot(orderId);
            String candidateVersion = String.valueOf(plan.arguments().get("candidateVersion"));
            String candidateRound = String.valueOf(plan.arguments().get("candidateRound"));
            if (candidate.version() == null || candidate.version() != Long.parseLong(candidateVersion)
                    || candidate.round() == null || candidate.round() != Long.parseLong(candidateRound)) {
                throw new ToolException("version_conflict", "候选人状态已变化，请重新预览");
            }
            if (!"0".equals(candidate.state()) && !"4".equals(candidate.state())) {
                throw new ToolException("state_conflict", "已不是待响应候选人");
            }
        }
    }

    /** 确认事务只做领域认领检查，不写业务数据。 */
    @Override
    public void reserve(ToolOperationContext context, Plan plan) {
        revalidate(context, plan);
    }

    /** 执行写操作。 */
    @Override
    public Outcome execute(ToolOperationContext context, Plan plan) {
        try {
            IMealExecutionService.OrderResult result;
            if (SUBMIT.equals(toolName)) {
                result = executions.submitOrder(submitCommand(plan.arguments()));
            } else if (CANCEL.equals(toolName)) {
                result = executions.cancelOrder(Long.parseLong(plan.targetId()), Long.parseLong(plan.expectedVersion()));
            } else if (ACCEPT.equals(toolName)) {
                result = executions.acceptOrder(Long.parseLong(plan.targetId()), Long.parseLong(plan.expectedVersion()),
                        Long.parseLong(String.valueOf(plan.arguments().get("candidateVersion"))),
                        Long.parseLong(String.valueOf(plan.arguments().get("candidateRound"))));
            } else if (REFUSE.equals(toolName)) {
                result = executions.refuseOrder(Long.parseLong(plan.targetId()), Long.parseLong(plan.expectedVersion()),
                        Long.parseLong(String.valueOf(plan.arguments().get("candidateVersion"))),
                        Long.parseLong(String.valueOf(plan.arguments().get("candidateRound"))));
            } else {
                result = executions.finishOrder(Long.parseLong(plan.targetId()), Long.parseLong(plan.expectedVersion()));
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("orderId", result.orderId());
            evidence.put("orderNo", result.orderNo());
            evidence.put("status", result.status());
            evidence.put("version", result.version() == null ? null : String.valueOf(result.version()));
            evidence.put("closeReason", result.closeReason());
            evidence.put("acceptUserId", result.acceptUserId() == null ? null
                    : String.valueOf(result.acceptUserId()));
            return new Outcome(OutcomeKind.APPLIED, "LOCAL", resultCode(result), evidence);
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
        if (SUBMIT.equals(toolName) || CANCEL.equals(toolName) || FINISH.equals(toolName)) {
            result.put("orderId", outcome.evidence().get("orderId"));
            result.put("orderNo", outcome.evidence().get("orderNo"));
            result.put("status", outcome.evidence().get("status"));
            result.put("version", outcome.evidence().get("version"));
            if (CANCEL.equals(toolName)) {
                result.put("closeReason", outcome.evidence().get("closeReason"));
            }
        } else {
            result.put("orderId", outcome.evidence().get("orderId"));
            result.put("status", outcome.evidence().get("status"));
            result.put("version", outcome.evidence().get("version"));
            result.put("acceptUserId", outcome.evidence().get("acceptUserId"));
            result.put("closeReason", outcome.evidence().get("closeReason"));
        }
        return result;
    }

    /** 本地事务回滚即未生效。 */
    @Override
    public void releaseUnapplied(ToolOperationContext context, Plan plan, String reasonCode) {
        // 无额外占用。
    }

    /** 下单计划。 */
    private Plan submitPlan(Map<String, Object> arguments) {
        List<IMealExecutionService.OrderLine> lines = new ArrayList<>();
        Object rawItems = arguments.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
        Set<String> dishIds = new java.util.HashSet<>();
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> line)) {
                throw new IllegalArgumentException("items 元素格式不正确");
            }
            String dishId = String.valueOf(line.get("dishId"));
            if (!dishIds.add(dishId)) {
                throw new IllegalArgumentException("同一订单不能重复提交同一菜品");
            }
            int quantity = ((Number) line.get("quantity")).intValue();
            Object remark = line.get("remark");
            lines.add(new IMealExecutionService.OrderLine(Long.parseLong(dishId), quantity,
                    remark == null ? null : String.valueOf(remark)));
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("items", lines.stream().map(line -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dishId", String.valueOf(line.dishId()));
            item.put("quantity", line.quantity());
            item.put("remark", line.remark());
            return item;
        }).toList());
        plan.put("orderRemark", arguments.get("orderRemark"));
        plan.put("assignUserId", arguments.get("assignUserId"));
        IMealExecutionService.OrderPreview preview = executions.previewOrder(submitCommand(plan));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("itemCount", lines.size());
        summary.put("totalQuantity", lines.stream().mapToInt(IMealExecutionService.OrderLine::quantity).sum());
        summary.put("assigned", arguments.get("assignUserId") != null);
        summary.put("items", preview.items().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dishId", String.valueOf(item.dishId()));
            row.put("dishName", item.dishName());
            row.put("quantity", item.quantity());
            row.put("remark", item.remark());
            return row;
        }).toList());
        summary.put("orderRemark", preview.orderRemark());
        summary.put("assigneeName", preview.assigneeName() == null ? "公开接单" : preview.assigneeName());
        summary.put("assigneeId", preview.assignUserId() == null ? null : String.valueOf(preview.assignUserId()));
        summary.put("effect", "创建订单并通知本家庭厨师");
        return new Plan(plan, summary, "meal_order", null, null, previewDigest(preview), ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.MEAL_WRITE);
    }

    /** 指纹包含实际落单的菜名、封面、份数、备注及指派对象。 */
    private static String previewDigest(IMealExecutionService.OrderPreview preview) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(preview.toString());
    }

    /** 单订单计划。 */
    private Plan singleOrderPlan(Map<String, Object> arguments) {
        String orderId = String.valueOf(arguments.get("orderId"));
        String version = String.valueOf(arguments.get("expectedVersion"));
        IMealExecutionService.OrderSnapshot snapshot = executions.orderSnapshot(Long.parseLong(orderId));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("orderId", orderId);
        plan.put("expectedVersion", version);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderId", orderId);
        summary.put("expectedVersion", version);
        summary.put("currentStatus", snapshot.status());
        summary.put("effect", CANCEL.equals(toolName) ? "取消订单并使其所有候选人失效"
                : "把订单标记为已完成并通知点餐人");
        return new Plan(plan, summary, "meal_order", orderId, version, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.MEAL_WRITE);
    }

    /** 候选类计划。 */
    private Plan candidatePlan(Map<String, Object> arguments) {
        String orderId = String.valueOf(arguments.get("orderId"));
        String version = String.valueOf(arguments.get("expectedVersion"));
        String candidateVersion = String.valueOf(arguments.get("candidateVersion"));
        String candidateRound = String.valueOf(arguments.get("candidateRound"));
        IMealExecutionService.OrderSnapshot snapshot = executions.orderSnapshot(Long.parseLong(orderId));
        IMealExecutionService.CandidateSnapshot candidate = executions.candidateSnapshot(Long.parseLong(orderId));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("orderId", orderId);
        plan.put("expectedVersion", version);
        plan.put("candidateVersion", candidateVersion);
        plan.put("candidateRound", candidateRound);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderId", orderId);
        summary.put("expectedVersion", version);
        summary.put("candidateVersion", candidateVersion);
        summary.put("candidateRound", candidateRound);
        summary.put("currentStatus", snapshot.status());
        summary.put("effect", ACCEPT.equals(toolName) ? "接单后订单进入制作中，其他候选人随即失效"
                : "拒单后按家庭配置维持待接单、转公开或关闭订单");
        return new Plan(plan, summary, "meal_order", orderId, version, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.MEAL_WRITE);
    }

    /** 计划转下单命令。 */
    private IMealExecutionService.OrderSubmitCommand submitCommand(Map<String, Object> arguments) {
        Object rawItems = arguments.get("items");
        List<IMealExecutionService.OrderLine> lines = new ArrayList<>();
        Set<String> dishIds = new java.util.HashSet<>();
        for (Object raw : (List<?>) rawItems) {
            Map<?, ?> line = (Map<?, ?>) raw;
            String dishId = String.valueOf(line.get("dishId"));
            if (!dishIds.add(dishId)) {
                throw new IllegalArgumentException("同一订单不能重复提交同一菜品");
            }
            lines.add(new IMealExecutionService.OrderLine(Long.parseLong(dishId),
                    ((Number) line.get("quantity")).intValue(),
                    line.get("remark") == null ? null : String.valueOf(line.get("remark"))));
        }
        Object assignUserId = arguments.get("assignUserId");
        return new IMealExecutionService.OrderSubmitCommand(lines,
                arguments.get("orderRemark") == null ? null : String.valueOf(arguments.get("orderRemark")),
                assignUserId == null ? null : Long.parseLong(String.valueOf(assignUserId)));
    }

    /** 结果码。 */
    private String resultCode(IMealExecutionService.OrderResult result) {
        if (SUBMIT.equals(toolName)) {
            return "created";
        }
        if (ACCEPT.equals(toolName)) {
            return "accepted";
        }
        if (REFUSE.equals(toolName)) {
            return "refused";
        }
        if (CANCEL.equals(toolName)) {
            return "cancelled";
        }
        return "finished";
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
        return new ToolException("state_conflict", "当前状态不允许该操作");
    }
}
