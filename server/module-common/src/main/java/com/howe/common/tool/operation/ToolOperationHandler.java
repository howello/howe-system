package com.howe.common.tool.operation;

import java.util.Map;

/**
 * 领域操作 SPI。
 *
 * <p>每个领域在本模块内实现并由平台按代码目录精确绑定，不按模型输入反射调用服务。
 * Handler 只依赖本契约与所属领域 Service，不得注入 MCP 平台服务、网关自身或账本存储。</p>
 *
 * <p>实现必须幂等可恢复：同一操作重复派发时已完成的效果不得重复产生；
 * 无法证明是否生效时必须返回 {@link OutcomeKind#UNKNOWN}。</p>
 */
public interface ToolOperationHandler {

    /**
     * 返回与工具定义完全一致的代码注册键。
     *
     * @return 注册键
     */
    Key key();

    /**
     * 规范化输入、检查当前可访问性并生成计划；不得产生写入或外部副作用。
     *
     * @param context 可信上下文
     * @param arguments 工具业务参数
     * @return 规范化计划
     */
    Plan plan(ToolOperationContext context, Map<String, Object> arguments);

    /**
     * 按当前阶段重新校验业务权限、家庭、对象状态与版本或结果可见性。
     *
     * @param context 可信上下文
     * @param plan 已保存计划
     */
    void revalidate(ToolOperationContext context, Plan plan);

    /**
     * 在确认事务中认领领域资源；仅允许本地数据库写入。
     *
     * @param context 可信上下文
     * @param plan 已保存计划
     */
    void reserve(ToolOperationContext context, Plan plan);

    /**
     * 执行操作；本地型在平台事务内执行，外部型在事务外发送一次并记录可恢复证据。
     *
     * @param context 可信上下文
     * @param plan 已保存计划
     * @return 执行结果
     */
    Outcome execute(ToolOperationContext context, Plan plan);

    /**
     * 只对账既有外部效果，不重新发送创建、覆盖或其它外部写请求。
     *
     * @param context 可信上下文
     * @param plan 已保存计划
     * @return 对账结果
     */
    Outcome reconcile(ToolOperationContext context, Plan plan);

    /**
     * 在平台最终提交事务中补齐领域状态并产生安全结果。
     *
     * @param context 可信上下文
     * @param plan 已保存计划
     * @param outcome 已确认的执行结果
     * @return 安全结果
     */
    Map<String, Object> complete(ToolOperationContext context, Plan plan, Outcome outcome);

    /**
     * 声明父操作成功后需要平台继续执行的固定步骤。
     *
     * <p>默认没有任何后续步骤。返回的步骤在父操作成功事务内登记，
     * 与领域写入、父操作终态一起提交；任一步失败必须整体回滚。
     * 领域只给固定代码键和业务载荷，不执行调度，也不感知授权与租约。</p>
     *
     * @param context 可信上下文
     * @param plan 已保存计划
     * @param outcome 已确认的执行结果
     * @return 后续步骤声明；没有时返回空列表
     */
    default java.util.List<ToolOperationContinuation> continuations(ToolOperationContext context, Plan plan,
            Outcome outcome) {
        return java.util.List.of();
    }

    /**
     * 确知尚未发送或未生效时释放本地认领；不得释放结果未知的外部占用。
     *
     * @param context 可信上下文
     * @param plan 已保存计划
     * @param reasonCode 固定原因码
     */
    void releaseUnapplied(ToolOperationContext context, Plan plan, String reasonCode);

    /**
     * 代码注册键。
     *
     * @param toolName 工具名
     * @param toolVersion 工具版本
     */
    record Key(String toolName, String toolVersion) {
    }

    /**
     * 不含协议授权字段的规范化业务计划；完整版本由平台加密保存。
     *
     * @param arguments 规范化业务参数
     * @param safeSummary 安全摘要
     * @param targetType 目标类型
     * @param targetId 目标主键
     * @param expectedVersion 目标版本
     * @param configurationDigest 目标配置摘要
     * @param executionKind 执行模型
     * @param queueGroup 队列分组
     */
    record Plan(Map<String, Object> arguments, Map<String, Object> safeSummary, String targetType, String targetId,
            String expectedVersion, String configurationDigest, ExecutionKind executionKind, QueueGroup queueGroup) {

        /** 深度复制并校验执行模型。 */
        public Plan {
            arguments = ToolOperationValues.copyMap(arguments);
            safeSummary = ToolOperationValues.copyMap(safeSummary);
            if (targetType == null || targetType.isBlank()) {
                throw new IllegalArgumentException("计划缺少目标类型");
            }
            if (executionKind == null || queueGroup == null) {
                throw new IllegalArgumentException("计划缺少执行模型或队列分组");
            }
        }
    }

    /** 本地事务与需要外部对账的执行模型。 */
    enum ExecutionKind {
        /** 本地事务。 */
        LOCAL_TRANSACTION,
        /** 可对账的远端效果。 */
        REMOTE_RECONCILABLE
    }

    /** 首版使用的队列分组。 */
    enum QueueGroup {
        /** 本地写。 */
        LOCAL_WRITE,
        /** 博客发布。 */
        BLOG_PUBLICATION,
        /** 点餐本地写。 */
        MEAL_WRITE
    }

    /** 外部结果只有已确认、确知未生效和不确定三类。 */
    enum OutcomeKind {
        /** 已生效。 */
        APPLIED,
        /** 确知未生效。 */
        NOT_APPLIED,
        /** 结果未知。 */
        UNKNOWN
    }

    /**
     * 执行结果；{@code evidence} 仅供加密恢复使用，不能直接作为工具结果或审计正文。
     *
     * @param kind 结果种类
     * @param stage 阶段
     * @param resultCode 固定结果码
     * @param evidence 恢复证据
     */
    record Outcome(OutcomeKind kind, String stage, String resultCode, Map<String, Object> evidence) {

        /** 深度复制证据。 */
        public Outcome {
            evidence = ToolOperationValues.copyMap(evidence);
        }
    }
}
