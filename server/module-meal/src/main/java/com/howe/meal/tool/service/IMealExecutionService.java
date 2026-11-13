package com.howe.meal.tool.service;

import com.howe.meal.domain.vo.MealKitchenOrderSummary;
import java.util.List;

/**
 * 点餐写工具与厨房读取工具的领域执行服务。
 *
 * <p>把「可信身份下的领域校验 → 状态迁移 → 安全结果」收在模块内部；
 * Provider 与 Handler 只做参数规范化、计划与结果投影，不复制订单状态机。
 * 本服务不注入任何 MCP 平台类型。</p>
 */
public interface IMealExecutionService {

    /**
     * 厨房工作台视图。
     *
     * @param status 必填，{@code waiting} 或 {@code cooking}
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IMealReadService.PageResult<KitchenOrderView> listKitchenOrders(String status, int page, int pageSize);

    /**
     * 厨房待办角标，可见性与列表保持一致。
     *
     * @return 待接单与制作中数量
     */
    MealKitchenOrderSummary kitchenSummary();

    /**
     * 读取下单确认快照，按实际下单规则核对菜品与指派厨师。
     *
     * @param command 规范化下单命令
     * @return 可供用户核对的快照
     */
    OrderPreview previewOrder(OrderSubmitCommand command);

    /**
     * 提交订单。
     *
     * @param command 规范化下单命令
     * @return 新订单安全结果
     */
    OrderResult submitOrder(OrderSubmitCommand command);

    /**
     * 取消本人待接单订单。
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @return 取消结果
     */
    OrderResult cancelOrder(Long orderId, long expectedVersion);

    /**
     * 厨师接单。
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @param candidateVersion 期望的候选数据版本
     * @param candidateRound 期望的候选轮次
     * @return 接单结果
     */
    OrderResult acceptOrder(Long orderId, long expectedVersion, long candidateVersion, long candidateRound);

    /**
     * 厨师拒单。
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @param candidateVersion 期望的候选数据版本
     * @param candidateRound 期望的候选轮次
     * @return 拒单结果
     */
    OrderResult refuseOrder(Long orderId, long expectedVersion, long candidateVersion, long candidateRound);

    /**
     * 接单人完成订单。
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @return 完成结果
     */
    OrderResult finishOrder(Long orderId, long expectedVersion);

    /**
     * 读取订单当前可确认版本，用于 preview 提示。
     *
     * @param orderId 订单ID
     * @return 订单快照
     */
    OrderSnapshot orderSnapshot(Long orderId);

    /**
     * 读取候选快照；非候选人时返回不可见错误。
     *
     * @param orderId 订单ID
     * @return 候选快照
     */
    CandidateSnapshot candidateSnapshot(Long orderId);

    /**
     * 提交评价。
     *
     * @param orderId 订单ID
     * @param score 评分 1—5
     * @param content 评价内容
     * @param anonymous 是否匿名
     * @return 评价结果
     */
    ReviewResult submitReview(Long orderId, int score, String content, boolean anonymous);

    /**
     * 提交提案并返回后续自动审核所需载荷。
     *
     * @param command 规范化提案命令
     * @return 提案结果
     */
    ProposalSubmitResult submitProposal(ProposalSubmitCommand command);

    /**
     * 人工审核提案。
     *
     * @param proposalId 提案ID
     * @param expectedVersion 期望的提案数据版本
     * @param expectedStatus 期望的提案状态
     * @param decision 审核结论，{@code approved} 或 {@code rejected}
     * @param reason 审核意见
     * @return 审核结果
     */
    ProposalAuditResult auditProposal(Long proposalId, long expectedVersion, String expectedStatus, String decision,
            String reason);

    /**
     * 提案快照，用于 preview 提示。
     *
     * @param proposalId 提案ID
     * @return 提案快照
     */
    ProposalSnapshot proposalSnapshot(Long proposalId);

    /**
     * 厨房订单视图。
     *
     * @param order 订单
     * @param candidateState 本人候选状态，非候选人为 null
     * @param candidateVersion 本人候选版本，非候选人为 null
     * @param candidateRound 当前候选轮次
     * @param items 有界明细摘要
     * @param itemsTotal 明细总数
     */
    record KitchenOrderView(String orderId, String orderNo, String status, Long version, Long candidateRound,
            String assignMode, String userName, String deadlineTime, String candidateState, Long candidateVersion,
            List<ItemView> items, int itemsTotal) {
    }

    /**
     * 订单明细摘要。
     *
     * @param dishName 菜名快照
     * @param count 份数
     */
    record ItemView(String dishName, Integer count) {
    }

    /**
     * 订单安全结果。
     *
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param status 状态
     * @param version 数据版本
     * @param closeReason 关闭原因
     * @param acceptUserId 接单人用户ID
     */
    record OrderResult(String orderId, String orderNo, String status, Long version, String closeReason,
            Long acceptUserId) {
    }

    /**
     * 订单确认快照。
     *
     * @param orderId 订单ID
     * @param status 状态
     * @param version 数据版本
     * @param candidateRound 候选轮次
     */
    record OrderSnapshot(String orderId, String status, Long version, Long candidateRound) {
    }

    /**
     * 候选确认快照。
     *
     * @param orderId 订单ID
     * @param state 候选状态
     * @param version 候选数据版本
     * @param round 候选轮次
     */
    record CandidateSnapshot(String orderId, String state, Long version, Long round) {
    }

    /**
     * 评价结果。
     *
     * @param reviewId 评价ID
     * @param orderId 订单ID
     */
    record ReviewResult(String reviewId, String orderId) {
    }

    /**
     * 提案提交结果。
     *
     * @param proposalId 提案ID
     * @param version 提交后的数据版本
     * @param status 状态
     */
    record ProposalSubmitResult(String proposalId, Long version, String status) {
    }

    /**
     * 提案审核结果。
     *
     * @param proposalId 提案ID
     * @param status 审核后状态
     * @param version 数据版本
     * @param dishId 关联菜品ID
     * @param dishEffect 菜品影响，{@code created}/{@code listed}/{@code unlisted}/{@code unchanged}
     */
    record ProposalAuditResult(String proposalId, String status, Long version, String dishId, String dishEffect) {
    }

    /**
     * 提案确认快照。
     *
     * @param proposalId 提案ID
     * @param status 状态
     * @param version 数据版本
     * @param dishId 关联菜品ID
     */
    record ProposalSnapshot(String proposalId, String status, Long version, String dishId) {
    }

    /**
     * 下单命令。
     *
     * @param items 明细，每项菜ID与份数
     * @param orderRemark 整单备注
     * @param assignUserId 指派厨师用户ID
     */
    record OrderSubmitCommand(List<OrderLine> items, String orderRemark, Long assignUserId) {
    }

    /**
     * 下单明细。
     *
     * @param dishId 菜品ID
     * @param quantity 份数
     * @param remark 单项备注
     */
    record OrderLine(Long dishId, int quantity, String remark) {
    }

    /** 下单确认快照。 */
    record OrderPreview(List<PreviewLine> items, String orderRemark, Long assignUserId, String assigneeName) {
    }

    /** 菜品确认快照；封面只参与变化检测，不向确认页展示。 */
    record PreviewLine(Long dishId, String dishName, String cover, int quantity, String remark) {
    }

    /**
     * 提案提交命令。
     *
     * @param name 菜名
     * @param description 介绍
     * @param reason 想吃的理由
     * @param notes 补充说明，落库到 tips
     * @param image 参考图
     * @param categoryId 唯一主类
     */
    record ProposalSubmitCommand(String name, String description, String reason, String notes, String image,
            Long categoryId) {
    }
}
