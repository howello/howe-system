package com.howe.meal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.core.redis.RedisCache;
import com.howe.meal.domain.MealNotification;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.service.IMealNotifyService;
import com.howe.meal.service.ITransactionCommitExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 点餐订单通知服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealNotifyServiceImpl implements IMealNotifyService, ITransactionCommitExecutor {

    private static final long NOTIFICATION_TTL_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();
    private long lastNotificationTimestamp = -1L;
    private int nextSuffix;
    private final java.util.Set<Integer> suffixesInCurrentMillisecond = new java.util.HashSet<>();
    private final RedisCache redisCache;
    private final MealOrderMapper mealOrderMapper;
    private final ObjectMapper objectMapper;

    /**
     * 推送通知前校验接收队列键，避免写入意外命名空间。
     *
     * @param receiverKey 接收者队列键
     */
    private void validateReceiverKey(String receiverKey) {
        if (receiverKey == null || !(receiverKey.startsWith("notify:user:")
                || receiverKey.startsWith("notify:chef:"))) {
            throw new IllegalArgumentException("通知接收队列键无效");
        }
    }

    @Override
    public void push(String receiverKey, String type, Long orderId, String title, String content,
            Map<String, Object> extra) {
        try {
            validateReceiverKey(receiverKey);
        } catch (Exception e) {
            log.warn("拒绝写入非法点餐通知队列，receiverKey={}", receiverKey, e);
            return;
        }
        long createTime = System.currentTimeMillis();
        MealNotification notification = new MealNotification();
        notification.setType(type);
        notification.setOrderId(orderId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setExtra(extra == null ? new HashMap<>() : extra);
        try {
            for (int attempt = 0; attempt < 64; attempt++) {
                String notificationId = createNotificationId(createTime);
                notification.setId(notificationId);
                notification.setCreateTime(createTime);
                String member = objectMapper.writeValueAsString(notification);
                String idKey = "notify:id:" + notificationId;
                if (redisCache.addNotificationIfAbsent(receiverKey, idKey, member, createTime,
                        NOTIFICATION_TTL_DAYS, TimeUnit.DAYS)) {
                    return;
                }
                createTime = Math.max(System.currentTimeMillis(), createTime);
            }
            log.warn("生成唯一点餐通知ID失败，receiverKey={}, type={}, orderId={}", receiverKey, type, orderId);
        } catch (Exception e) {
            log.warn("写入点餐通知失败，receiverKey={}, type={}, orderId={}", receiverKey, type, orderId, e);
        }
    }

    @Override
    public List<MealNotification> poll(String receiverKey) {
        List<MealNotification> notifications = new ArrayList<>();
        try {
            validateReceiverKey(receiverKey);
            List<String> members = redisCache.pollNotifications(receiverKey);
            for (String member : members) {
                try {
                    notifications.add(objectMapper.readValue(member, MealNotification.class));
                } catch (Exception e) {
                    log.warn("忽略无法解析的点餐通知成员，receiverKey={}", receiverKey, e);
                }
            }
        } catch (Exception e) {
            log.warn("读取点餐通知失败，receiverKey={}", receiverKey, e);
        }
        return notifications;
    }

    @Override
    public void pushNewOrderToChefs(Long deptId, Long orderId, String orderNo, String userName, Integer totalCount) {
        try {
            List<Long> chefIds = mealOrderMapper.selectChefUserIdsByDeptId(deptId);
            for (Long chefId : chefIds) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("orderNo", orderNo);
                push("notify:chef:" + chefId, "NEW_ORDER", orderId, "新订单", userName + "下单"
                        + (totalCount == null ? 0 : totalCount) + " 份，等待接单", extra);
            }
        } catch (Exception e) {
            log.warn("查询点餐厨师通知接收人失败，deptId={}, orderId={}", deptId, orderId, e);
        }
    }

    /**
     * 创建同一服务实例内唯一的毫秒时间戳与四位后缀组合。
     *
     * @param requestedTime 通知创建时间戳
     * @return 通知唯一 ID
     */
    private synchronized String createNotificationId(long requestedTime) {
        long timestamp = Math.max(requestedTime, System.currentTimeMillis());
        if (timestamp > lastNotificationTimestamp) {
            lastNotificationTimestamp = timestamp;
            nextSuffix = RANDOM.nextInt(10_000);
            suffixesInCurrentMillisecond.clear();
        } else {
            timestamp = lastNotificationTimestamp;
        }
        if (suffixesInCurrentMillisecond.size() == 10_000) {
            timestamp++;
            lastNotificationTimestamp = timestamp;
            nextSuffix = RANDOM.nextInt(10_000);
            suffixesInCurrentMillisecond.clear();
        }
        int suffix = nextSuffix;
        while (!suffixesInCurrentMillisecond.add(suffix)) {
            suffix = (suffix + 1) % 10_000;
        }
        nextSuffix = (suffix + 1) % 10_000;
        return String.format("n_%d_%04d", timestamp, suffix);
    }

    /**
     * 在事务提交后注册通知回调；未处于事务中时立即执行。
     *
     * @param callback 提交后回调
     */
    @Override
    public void afterCommit(Runnable callback) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()
                && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                callback.run();
                            } catch (Exception e) {
                                log.warn("事务提交后执行订单通知回调失败", e);
                            }
                        }
                    });
        } else {
            try {
                callback.run();
            } catch (Exception e) {
                log.warn("事务外执行订单通知回调失败", e);
            }
        }
    }
}
