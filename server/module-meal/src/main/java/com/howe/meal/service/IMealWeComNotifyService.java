package com.howe.meal.service;

import com.howe.meal.domain.MealAppVersion;

/**
 * 点餐-企业微信通知发送服务
 *
 * <p>把订单、AI 异步任务与 App 新版本事件转成 text 文案、解析 @ 手机号后调用公共客户端。
 * 调用方负责选择正确的触发时机（订单事件必须在事务提交后），本服务内部不重试、
 * 不抛异常，任何失败只记录日志。</p>
 *
 * @author howe
 */
public interface IMealWeComNotifyService {

    /**
     * 发送新订单通知
     *
     * @param orderId 订单ID
     */
    void notifyNewOrder(Long orderId);

    /**
     * 发送接单通知
     *
     * @param orderId 订单ID
     */
    void notifyOrderAccepted(Long orderId);

    /**
     * 发送订单完成通知
     *
     * @param orderId 订单ID
     */
    void notifyOrderCompleted(Long orderId);

    /**
     * 发送无人接单通知（超时或全部候选人拒绝）
     *
     * @param orderId 订单ID
     */
    void notifyOrderUnaccepted(Long orderId);

    /**
     * 发送收到评价通知
     *
     * @param orderId  订单ID
     * @param score    评分
     * @param content  评价内容
     */
    void notifyOrderRated(Long orderId, Integer score, String content);

    /**
     * 发送菜品 AI 更新结果通知
     *
     * @param deptId   家庭ID
     * @param dishId   菜品ID
     * @param dishName 菜名
     * @param userId   发起更新的用户ID
     * @param success  是否成功
     * @param detail   结果说明（写入字段或失败原因）
     * @param model    实际使用的模型名，可为空
     */
    void notifyDishAiUpdate(Long deptId, Long dishId, String dishName, Long userId, boolean success, String detail,
            String model);

    /**
     * 发送提案 AI 审核结果通知
     *
     * @param deptId     家庭ID
     * @param proposalId 提案ID
     * @param name       菜名
     * @param userId     提案提交人ID
     * @param result     结果说明（通过 / 驳回 / 审核未完成）
     * @param reason     可读原因
     * @param model      实际使用的模型名，可为空
     */
    void notifyProposalAiAudit(Long deptId, Long proposalId, String name, Long userId, String result, String reason,
            String model);

    /**
     * 发送 App 新版本发布通知
     *
     * <p>平台级事件：向所有启用通知且已配置 Webhook Key 的家庭群各发一条，
     * 只受家庭级总开关约束，不受订单 / AI 事件开关影响。</p>
     *
     * @param version 本次发布的版本记录
     */
    void notifyAppRelease(MealAppVersion version);
}
