package com.howe.meal.tool.dto;

import java.util.List;

/**
 * 点餐只读工具的对外数据载体。
 *
 * <p>只承载工具栏白名单字段：不返回部门 ID、创建者、账号、手机号、备注原文或明细备注。
 * 可见性用 {@code public} / {@code family} 表达，而不是把内部 {@code deptId} 透给客户端。</p>
 */
public final class MealReadDtos {

    /** 工具数据载体不允许实例化。 */
    private MealReadDtos() {
    }

    /**
     * 分类视图。
     *
     * @param categoryId 分类 ID
     * @param name 名称
     * @param description 说明
     * @param icon 图标
     * @param sort 排序
     * @param status 状态，{@code enabled} 或 {@code disabled}
     * @param visibility 可见性，{@code public} 或 {@code family}
     */
    public record CategoryView(String categoryId, String name, String description, String icon, Integer sort,
            String status, String visibility) {
    }

    /**
     * 菜品摘要。
     *
     * @param dishId 菜品 ID
     * @param name 名称
     * @param description 简介
     * @param status 状态，{@code available} 或 {@code unavailable}
     * @param visibility 可见性
     * @param categoryIds 分类 ID
     * @param dataVersion 数据版本
     */
    public record DishSummary(String dishId, String name, String description, String status, String visibility,
            List<String> categoryIds, Long dataVersion) {
    }

    /**
     * 菜品详情。
     *
     * @param dishId 菜品 ID
     * @param name 名称
     * @param description 简介
     * @param status 状态
     * @param visibility 可见性
     * @param categoryIds 分类 ID
     * @param dataVersion 数据版本
     * @param ingredients 用料，白名单结构 {@code name}/{@code amount}
     * @param steps 做法
     * @param tips 小贴士
     * @param duration 耗时
     * @param level 难度
     * @param serve 份量
     * @param kcal 热量
     * @param tags 标签
     */
    public record DishDetail(String dishId, String name, String description, String status, String visibility,
            List<String> categoryIds, Long dataVersion, List<java.util.Map<String, Object>> ingredients,
            List<String> steps, List<String> tips, String duration, String level, String serve, String kcal,
            List<String> tags) {
    }

    /**
     * 订单明细摘要。
     *
     * @param dishId 菜品 ID
     * @param dishName 菜品名快照
     * @param count 份数
     */
    public record OrderItemView(String dishId, String dishName, Integer count) {
    }

    /**
     * 订单视图。
     *
     * @param orderId 订单 ID
     * @param orderNo 订单号
     * @param status 状态
     * @param closeReason 关闭原因，未关闭时为 null
     * @param createdAt 创建时间
     * @param acceptTime 接单时间
     * @param finishTime 完成时间
     * @param cancelTime 取消时间
     * @param deadlineTime 接单截止时间
     * @param totalCount 总份数
     * @param items 明细摘要
     * @param reviewed 是否已评价
     */
    public record OrderView(String orderId, String orderNo, String status, String closeReason, String createdAt,
            String acceptTime, String finishTime, String cancelTime, String deadlineTime, Integer totalCount,
            List<OrderItemView> items, Boolean reviewed) {
    }

    /**
     * 提案列表项。
     *
     * @param proposalId 提案 ID
     * @param name 菜名
     * @param status 状态
     * @param categoryIds 分类 ID
     * @param categoryNames 分类名称
     */
    public record ProposalSummary(String proposalId, String name, String status, List<String> categoryIds,
            String categoryNames) {
    }

    /**
     * 提案详情。
     *
     * @param proposalId 提案 ID
     * @param name 菜名
     * @param status 状态
     * @param categoryIds 分类 ID
     * @param categoryNames 分类名称
     * @param description 描述
     * @param tags 标签
     * @param duration 耗时
     * @param level 难度
     * @param ingredients 用料，白名单结构 {@code name}/{@code amount}
     * @param steps 做法
     * @param tips 小贴士
     * @param reason 提交理由
     * @param auditTime 审核时间
     * @param auditRemark 审核意见
     * @param dishId 关联菜品 ID
     */
    public record ProposalDetail(String proposalId, String name, String status, List<String> categoryIds,
            String categoryNames, String description, List<String> tags, String duration, String level,
            List<java.util.Map<String, Object>> ingredients, List<String> steps, List<String> tips, String reason,
            String auditTime, String auditRemark, String dishId) {
    }

    /**
     * 评价视图。
     *
     * @param reviewId 评价 ID
     * @param orderId 订单 ID
     * @param score 评分
     * @param content 评价正文，可能被截断
     * @param anonymous 是否匿名
     * @param createdAt 创建时间
     * @param dishNames 菜品名
     */
    public record ReviewView(String reviewId, String orderId, Integer score, String content, boolean anonymous,
            String createdAt, String dishNames) {
    }

    /**
     * 可指派厨师视图。
     *
     * @param userId 用户 ID
     * @param displayName 展示名
     */
    public record AssigneeView(String userId, String displayName) {
    }
}
