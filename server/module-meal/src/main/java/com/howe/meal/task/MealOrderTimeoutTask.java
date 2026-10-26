package com.howe.meal.task;

import com.howe.meal.domain.MealOrder;
import com.howe.meal.mapper.MealOrderCandidateMapper;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.service.IMealOrderCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点餐订单待接单兜底任务
 *
 * <p>每 30 秒执行两类扫描：</p>
 * <ol>
 *   <li><b>超时扫描</b>：仍待接单且已过截止时间的订单。公开单直接关闭为无人接单，
 *       指派单按 {@code meal.order.assignRefuseStrategy} 决定转公开还是关闭。</li>
 *   <li><b>全员拒绝兜底</b>：仍待接单、但已没有任何待响应候选人的订单。
 *       正常情况下最后一名候选人拒绝时就会在同一事务内关闭，这里只用于防并发或异常导致的悬挂。</li>
 * </ol>
 *
 * <p>两类关闭都是带前置状态的条件更新，因此多实例部署、重复扫描或扫描期间刚好被接单时，
 * 只有真正关闭成功的那一次会发通知，不需要额外的幂等表。这一处扫描同时承担
 * 「延时触发」与「兜底」两件事，因此没有引入 MQ 或 Redis 延时队列。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MealOrderTimeoutTask {

    /** 单批处理上限，避免一次事务过长 */
    private static final int BATCH_LIMIT = 200;

    private final MealOrderMapper mealOrderMapper;
    private final MealOrderCandidateMapper mealOrderCandidateMapper;
    private final IMealOrderCandidateService mealOrderCandidateService;

    /**
     * 扫描并关闭超时未接单的订单
     *
     * <p>单条处理失败只记录日志，不中断同批其余订单；下一轮扫描会继续兜底。</p>
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void closeExpiredOrders() {
        List<Long> orderIds;
        try {
            orderIds = mealOrderMapper.selectExpiredWaitingOrderIds(BATCH_LIMIT);
        } catch (Exception e) {
            log.warn("扫描超时未接单订单失败", e);
            return;
        }
        if (orderIds.isEmpty()) {
            return;
        }
        int closed = 0;
        for (Long orderId : orderIds) {
            try {
                MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
                if (order == null) {
                    continue;
                }
                if (mealOrderCandidateService.handleTimeout(order)) {
                    closed++;
                }
            } catch (Exception e) {
                log.warn("处理超时未接单订单失败，orderId={}", orderId, e);
            }
        }
        if (closed > 0) {
            log.info("本轮超时订单处理完成，扫描 {} 条，实际关闭 {} 条", orderIds.size(), closed);
        }
    }

    /**
     * 兜底关闭「全员已拒绝但订单仍待接单」的订单
     *
     * <p>正常路径下最后一名候选人拒绝时订单已即时关闭；这里只捕获并发或异常留下的悬挂订单。
     * 关闭同样带前置状态条件，已被接单或已关闭的订单会被静默跳过。</p>
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 45_000)
    public void closeAllRefusedOrders() {
        List<Long> orderIds;
        try {
            orderIds = mealOrderMapper.selectWaitingWithoutPendingCandidate(BATCH_LIMIT);
        } catch (Exception e) {
            log.warn("扫描全员拒绝订单失败", e);
            return;
        }
        if (orderIds.isEmpty()) {
            return;
        }
        int closed = 0;
        for (Long orderId : orderIds) {
            try {
                MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
                if (order == null) {
                    continue;
                }
                // 再确认一次确实没有待响应候选人，避免与并发接单交错
                if (mealOrderCandidateMapper.countPending(orderId) > 0) {
                    continue;
                }
                if (mealOrderCandidateService.closeAllRefused(order)) {
                    closed++;
                }
            } catch (Exception e) {
                log.warn("关闭全员拒绝订单失败，orderId={}", orderId, e);
            }
        }
        if (closed > 0) {
            log.info("本轮全员拒绝兜底完成，扫描 {} 条，实际关闭 {} 条", orderIds.size(), closed);
        }
    }
}
