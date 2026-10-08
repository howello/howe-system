package com.howe.meal.service;

import com.howe.meal.domain.MealNotification;

import java.util.List;
import java.util.Map;

/**
 * 点餐订单通知服务。
 */
public interface IMealNotifyService {

    /**
     * 将订单通知写入指定接收者队列。
     *
     * @param receiverKey Redis 队列键
     * @param type 通知类型
     * @param orderId 订单ID
     * @param title 通知标题
     * @param content 通知内容
     * @param extra 附加数据
     */
    void push(String receiverKey, String type, Long orderId, String title, String content, Map<String, Object> extra);

    /**
     * 原子读取并清空通知队列。
     *
     * @param receiverKey Redis 队列键
     * @return 通知列表
     */
    List<MealNotification> poll(String receiverKey);

    /**
     * 向指定家庭中具有厨师角色的每位用户发送新订单通知。
     *
     * @param deptId 所属家庭ID
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param userName 点餐人名称
     * @param totalCount 菜品份数
     */
    void pushNewOrderToChefs(Long deptId, Long orderId, String orderNo, String userName, Integer totalCount);
}
