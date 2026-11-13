package com.howe.common.tool.operation;

import com.howe.common.tool.model.ToolExecutionContext;
import java.time.Instant;
import java.util.Map;

/**
 * 平台每次实际调用领域 Handler 时构造的最小业务上下文。
 *
 * <p>不含 token、grant 密文、OAuth scope 或协议会话；{@code identity} 由平台在
 * 已认证与已授权后构造，领域不能自行拼造。</p>
 *
 * @param identity 可信业务身份
 * @param phase 本次调用阶段
 * @param operationId 操作标识；预览可为 {@code null}
 * @param fence 当前执行围栏；预览/准备为 0
 * @param evaluationTime 本次评估时间，用于冻结默认值
 * @param executionDeadlineAt 执行截止时间，可为 {@code null}
 * @param leaseGuard 租约检查能力
 */
public record ToolOperationContext(ToolExecutionContext identity, Phase phase, String operationId, long fence,
        Instant evaluationTime, Instant executionDeadlineAt, LeaseGuard leaseGuard) {

    /** 阶段决定本次检查是执行守卫、对账守卫还是结果可见性检查。 */
    public enum Phase {
        /** 预览。 */
        PREVIEW,
        /** 准备。 */
        PREPARE,
        /** 浏览器确认。 */
        CONFIRM,
        /** 实际执行。 */
        EXECUTE,
        /** 外部结果对账。 */
        RECONCILE,
        /** 结果可见性检查。 */
        RESULT_READ
    }

    /** 由平台提供的租约检查能力，不向领域暴露账本服务。 */
    @FunctionalInterface
    public interface LeaseGuard {
        /**
         * 失去租约、fence 或执行许可时抛受控异常，调用者必须停止后续动作。
         */
        void check();

        /**
         * 原子提交外部发送意图；平台实现应在检查有效 owner/fence/lease 后持久化 SEND_INTENT。
         *
         * <p>默认仅检查，用于本地型操作；远端可对账操作必须由平台作用域覆盖此方法。</p>
         */
        default void checkpointExternalSendIntent() {
            check();
        }

        /**
         * ledger 阶段与远端 publication 回执同事务推进。
         *
         * @param contentSha 远端文件 SHA，可为空时拒绝成功推进
         * @param commitSha 远端提交 SHA，可为空时拒绝成功推进
         */
        default void checkpointRemoteCommitted(Runnable persistDomainReceipt) {
            check();
            persistDomainReceipt.run();
        }

        /**
         * 在恢复事务中同时登记领域远端证据与 ledger 阶段；不发起新写。
         *
         * @param persistDomainReceipt 只更新本地 publication 回执的动作
         */
        default void checkpointReconciledRemoteCommitted(Runnable persistDomainReceipt) {
            checkpointRemoteCommitted(persistDomainReceipt);
        }

        /**
         * 原子确认当前恢复 worker 的 owner/fence/lease，可用于 publication fence 接管。
         *
         * <p>默认只检查；平台恢复上下文应覆盖此方法。该方法不得启动新的外部写。</p>
         */
        default void checkpointRecoveryFence() {
            check();
        }

        /**
         * 在同一 DataSource 的短事务中核对 ledger lease/fence 并执行 publication fence CAS。
         *
         * @param action 与 ledger 核验同事务执行的领域本地动作
         */
        default void inFenceTransaction(Runnable action) {
            check();
            action.run();
        }
    }

    /** 校验必要字段。 */
    public ToolOperationContext {
        if (identity == null || phase == null) {
            throw new IllegalArgumentException("操作上下文缺少身份或阶段");
        }
        if (evaluationTime == null) {
            throw new IllegalArgumentException("操作上下文缺少评估时间");
        }
        if (leaseGuard == null) {
            throw new IllegalArgumentException("操作上下文缺少租约检查");
        }
    }
}
