package com.howe.meal.service;

import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderCandidate;

import java.util.List;

/**
 * 点餐-订单候选厨师 服务层
 *
 * <p>负责候选人生成、拒单分流、指派转公开与订单关闭时的候选人收尾。
 * 所有状态迁移都以数据库条件更新裁决；并发正确性依赖订单行锁与前置状态条件。</p>
 *
 * @author howe
 */
public interface IMealOrderCandidateService {
    /**
     * 为订单生成候选人
     *
     * <p>指派模式只生成被指派人一行；公开模式生成本家庭全部符合资格的厨师。
     * 候选人为空时抛业务异常，调用方据此拒绝下单。</p>
     *
     * @param order        已落库的订单
     * @param assignUserId 指派的厨师用户ID，为空表示公开模式
     * @return 生成的候选人列表（已落库）
     */
    List<MealOrderCandidate> createCandidates(MealOrder order, Long assignUserId);

    /**
     * 候选人拒绝订单
     *
     * <p>先锁定订单行串行化并发拒绝，再标记自己已拒绝；若已无待响应候选人，
     * 按订单类型与配置策略关闭订单或转公开。</p>
     *
     * @param orderId 订单ID
     */
    void refuseOrder(Long orderId);

    /**
     * 处理超时：按订单模式与策略关闭或转公开
     *
     * <p>调用前订单必须仍是待接单；关闭使用条件更新，竞争失败时静默返回。</p>
     *
     * @param order 已过截止时间的待接单订单
     * @return 是否实际处理成功
     */
    boolean handleTimeout(MealOrder order);

    /**
     * 关闭「全员已拒绝」的订单（定时兜底）
     *
     * <p>正常路径下最后一名候选人拒绝时订单已即时关闭，这里只处理并发或异常留下的悬挂订单。</p>
     *
     * @param order 仍待接单但已无待响应候选人的订单
     * @return 是否实际关闭成功
     */
    boolean closeAllRefused(MealOrder order);

    /**
     * 把订单的全部候选人置为已失效
     *
     * @param orderId 订单ID
     */
    void invalidateAll(Long orderId);
}
