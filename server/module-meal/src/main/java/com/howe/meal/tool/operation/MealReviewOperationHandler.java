package com.howe.meal.tool.operation;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.meal.tool.service.IMealExecutionService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 点餐评价写操作处理器。
 *
 * <p>一单一评由领域唯一键与前置查询共同裁决；不同幂等键重复评价返回状态冲突，
 * 不能把别的操作生成的评价冒充当前操作成功。</p>
 */
public final class MealReviewOperationHandler implements ToolOperationHandler {

    /** 工具名。 */
    private static final String TOOL = "meal_review_submit";

    private final IMealExecutionService executions;

    /**
     * 构造评价写处理器。
     *
     * @param executions 领域执行服务
     */
    public MealReviewOperationHandler(IMealExecutionService executions) {
        this.executions = Objects.requireNonNull(executions);
    }

    @Override
    public Key key() {
        return new Key(TOOL, "1");
    }

    /** 规范化评价计划。 */
    @Override
    public Plan plan(ToolOperationContext context, Map<String, Object> arguments) {
        String orderId = String.valueOf(arguments.get("orderId"));
        int score = ((Number) arguments.get("score")).intValue();
        String content = arguments.get("content") == null ? null : String.valueOf(arguments.get("content"));
        boolean anonymous = Boolean.TRUE.equals(arguments.get("anonymous"));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("orderId", orderId);
        plan.put("score", score);
        plan.put("content", content);
        plan.put("anonymous", anonymous);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderId", orderId);
        summary.put("score", score);
        summary.put("anonymous", anonymous);
        summary.put("contentChars", content == null ? 0 : content.length());
        summary.put("effect", anonymous ? "以匿名身份留下评价" : "以当前昵称留下评价");
        return new Plan(plan, summary, "meal_review", orderId, null, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.MEAL_WRITE);
    }

    /** 重读订单可见性；一单一评在领域事务内裁决。 */
    @Override
    public void revalidate(ToolOperationContext context, Plan plan) {
        executions.orderSnapshot(Long.parseLong(plan.targetId()));
    }

    /** 确认事务不做额外占用。 */
    @Override
    public void reserve(ToolOperationContext context, Plan plan) {
        revalidate(context, plan);
    }

    /** 执行评价写入。 */
    @Override
    public Outcome execute(ToolOperationContext context, Plan plan) {
        try {
            IMealExecutionService.ReviewResult result = executions.submitReview(
                    Long.parseLong(plan.targetId()), ((Number) plan.arguments().get("score")).intValue(),
                    plan.arguments().get("content") == null ? null
                            : String.valueOf(plan.arguments().get("content")),
                    Boolean.TRUE.equals(plan.arguments().get("anonymous")));
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("reviewId", result.reviewId());
            evidence.put("orderId", result.orderId());
            return new Outcome(OutcomeKind.APPLIED, "LOCAL", "created", evidence);
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
        result.put("reviewId", outcome.evidence().get("reviewId"));
        result.put("orderId", outcome.evidence().get("orderId"));
        return result;
    }

    /** 本地事务回滚即未生效。 */
    @Override
    public void releaseUnapplied(ToolOperationContext context, Plan plan, String reasonCode) {
        // 无额外占用。
    }

    /** 业务异常收敛。 */
    private static ToolException translate(ServiceException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("已经评价")) {
            return new ToolException("state_conflict", "该订单已经评价过了");
        }
        if (message.contains("完成后")) {
            return new ToolException("state_conflict", "订单完成后才能评价");
        }
        if (message.contains("不存在") || message.contains("无权")) {
            return new ToolException("not_found_or_forbidden", "订单不存在或无权访问");
        }
        return new ToolException("state_conflict", "评价未完成");
    }
}
