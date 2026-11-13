package com.howe.meal.tool.config;

import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.meal.service.IMealMemberService;
import com.howe.meal.service.IMealProposalService;
import com.howe.meal.service.MealProposalAuditService;
import com.howe.meal.tool.provider.MealKitchenToolProvider;
import com.howe.meal.tool.provider.MealMemberToolProvider;
import com.howe.meal.tool.provider.MealProposalWriteToolProvider;
import com.howe.meal.tool.provider.MealReadToolProvider;
import com.howe.meal.tool.provider.MealWriteToolProvider;
import com.howe.meal.tool.service.IMealExecutionService;
import com.howe.meal.tool.service.IMealReadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安装 MCP 时装配点餐工具。
 *
 * <p>未安装时不创建 Provider，也不查询任何 MCP 表；读取 {@code howe.mcp.installed}
 * 配置键不需要引用 MCP Java 类型。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class MealToolConfiguration {

    /**
     * 注册点餐只读 Provider。
     *
     * @param reads 只读业务读取服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpMealReadTools(IMealReadService reads) {
        return new MealReadToolProvider(reads);
    }

    /**
     * 注册厨师工作台只读工具。
     *
     * @param executions 领域执行服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpMealKitchenTools(IMealExecutionService executions) {
        return new MealKitchenToolProvider(executions);
    }

    /**
     * 注册点餐写工具；只依赖 common 操作网关。
     *
     * @param gateway 操作网关
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpMealWriteTools(com.howe.common.tool.operation.ToolOperationGateway gateway) {
        return new MealWriteToolProvider(gateway);
    }

    /**
     * 注册提案写工具；只依赖 common 操作网关。
     *
     * @param gateway 操作网关
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpMealProposalWriteTools(
            com.howe.common.tool.operation.ToolOperationGateway gateway) {
        return new MealProposalWriteToolProvider(gateway);
    }

    /**
     * 注册家庭成员只读工具。
     *
     * @param members 成员服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpMealMemberTools(IMealMemberService members) {
        return new MealMemberToolProvider(members);
    }

    /**
     * 注册订单写操作处理器。
     *
     * @param executions 领域执行服务
     * @return 下单处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealOrderSubmitHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealOrderOperationHandler("meal_order_submit", executions);
    }

    /**
     * 注册取消订单处理器。
     *
     * @param executions 领域执行服务
     * @return 取消处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealOrderCancelHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealOrderOperationHandler("meal_order_cancel", executions);
    }

    /**
     * 注册接单处理器。
     *
     * @param executions 领域执行服务
     * @return 接单处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealOrderAcceptHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealOrderOperationHandler("meal_order_accept", executions);
    }

    /**
     * 注册拒单处理器。
     *
     * @param executions 领域执行服务
     * @return 拒单处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealOrderRefuseHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealOrderOperationHandler("meal_order_refuse", executions);
    }

    /**
     * 注册完成订单处理器。
     *
     * @param executions 领域执行服务
     * @return 完成处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealOrderFinishHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealOrderOperationHandler("meal_order_finish", executions);
    }

    /**
     * 注册评价处理器。
     *
     * @param executions 领域执行服务
     * @return 评价处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealReviewSubmitHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealReviewOperationHandler(executions);
    }

    /**
     * 注册提案提交处理器。
     *
     * @param executions 领域执行服务
     * @return 提交处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealProposalSubmitHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealProposalOperationHandler("meal_proposal_submit", executions);
    }

    /**
     * 注册提案审核处理器。
     *
     * @param executions 领域执行服务
     * @return 审核处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpMealProposalAuditHandler(
            IMealExecutionService executions) {
        return new com.howe.meal.tool.operation.MealProposalOperationHandler("meal_proposal_audit", executions);
    }

    /**
     * 注册提案自动审核步骤处理器。
     *
     * @param proposals 提案服务
     * @param audits 自动审核服务
     * @return 步骤处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationStepHandler mcpMealProposalAutoAuditStep(
            IMealProposalService proposals, MealProposalAuditService audits) {
        return new com.howe.meal.tool.operation.MealProposalAutoAuditStepHandler(proposals, audits);
    }
}
