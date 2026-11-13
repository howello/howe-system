package com.howe.common.tool.operation;

import com.howe.common.tool.model.ToolExecutionContext;
import java.time.Instant;
import java.util.Map;

/**
 * 后续步骤执行时由平台构造的可信上下文。
 *
 * <p>步骤仍属于原授权绑定：{@code identity} 是平台重新加载后的当前账号，
 * 授权复核与租约检查通过 {@code guard} 提供给领域，领域不能自行拼造或缓存。</p>
 *
 * @param identity 可信业务身份
 * @param parentOperationId 父操作标识
 * @param stepId 步骤标识
 * @param fence 步骤执行围栏
 * @param evaluationTime 本次评估时间
 * @param executionDeadlineAt 执行截止时间
 * @param guard 授权与租约守卫
 */
public record ToolOperationStepContext(ToolExecutionContext identity, String parentOperationId, String stepId,
        long fence, Instant evaluationTime, Instant executionDeadlineAt, Guard guard) {

    /** 校验必要字段。 */
    public ToolOperationStepContext {
        if (identity == null) {
            throw new IllegalArgumentException("步骤上下文缺少身份");
        }
        if (parentOperationId == null || parentOperationId.isBlank() || stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("步骤上下文缺少操作或步骤标识");
        }
        if (evaluationTime == null) {
            throw new IllegalArgumentException("步骤上下文缺少评估时间");
        }
        if (guard == null) {
            throw new IllegalArgumentException("步骤上下文缺少授权守卫");
        }
    }

    /**
     * 步骤执行期间的授权与围栏守卫。
     *
     * <p>实现必须重新加载账号、原授权、consent、工具开关、scope 与业务权限，
     * 不得沿用请求线程的缓存。撤权时抛受控异常，调用方必须立即停止后续外部调用与业务写入。</p>
     */
    @FunctionalInterface
    public interface Guard {

        /**
         * 复核授权并检查租约；失败时抛受控异常。
         */
        void check();

        /**
         * 在短事务中复核授权、锁定步骤围栏并执行本地动作。
         *
         * <p>用于把「外部调用结果应用为业务写入」与步骤终态放在同一事务里；
         * 撤权或围栏失效时必须整体回滚。</p>
         *
         * @param action 本地业务动作
         */
        default StepOutcome commitAuthorized(java.util.function.Supplier<StepOutcome> action) {
            check();
            return action.get();
        }

        /** 在有效围栏内持久化外部发送意图，恢复时不得盲目重发。 */
        default void externalSendIntent() {
            check();
        }
    }

    /**
     * 步骤执行结果。
     *
     * @param kind 结果种类
     * @param resultCode 稳定结果码
     * @param safeResult 可投影给用户的安全结果
     */
    public record StepOutcome(Kind kind, String resultCode, Map<String, Object> safeResult) {

        /** 防御性复制结果。 */
        public StepOutcome {
            safeResult = safeResult == null ? Map.of() : ToolOperationValues.copyMap(safeResult);
        }

        /** 已成功完成。 */
        public static StepOutcome applied(Map<String, Object> safeResult) {
            return new StepOutcome(Kind.APPLIED, "applied", safeResult);
        }

        /** 确知未生效。 */
        public static StepOutcome notApplied(String resultCode) {
            return new StepOutcome(Kind.NOT_APPLIED, resultCode, Map.of());
        }

        /** 结果未知，禁止自动重发。 */
        public static StepOutcome unknown(String resultCode) {
            return new StepOutcome(Kind.UNKNOWN, resultCode, Map.of());
        }

        /** 授权变化或被人工抢先，终止本步骤。 */
        public static StepOutcome aborted(String resultCode) {
            return new StepOutcome(Kind.ABORTED, resultCode, Map.of());
        }
    }

    /** 步骤结果种类。 */
    public enum Kind {
        /** 已完成。 */
        APPLIED,
        /** 确知未生效。 */
        NOT_APPLIED,
        /** 结果未知。 */
        UNKNOWN,
        /** 已终止，不再执行。 */
        ABORTED
    }
}
