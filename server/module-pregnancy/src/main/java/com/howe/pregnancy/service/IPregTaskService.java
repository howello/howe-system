package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.vo.TaskVO;

import java.util.List;

/**
 * 后台任务服务
 *
 * @author howe
 */
public interface IPregTaskService {

    /**
     * 查询任务摘要
     *
     * @param taskId 任务 ID
     * @return 任务摘要
     */
    TaskVO getTask(Long taskId);

    /**
     * 触发物品内容首次生成或显式重新生成
     *
     * @param itemId    物品 ID
     * @param version   物品版本
     * @param requestId 请求 ID
     * @return 任务摘要
     */
    TaskVO generateItemContent(Long itemId, Integer version, String requestId);

    /**
     * 重试当前生成任务中明确失败的阶段
     *
     * @param itemId 物品 ID
     * @return 任务摘要
     */
    TaskVO retryItemContent(Long itemId);

    /**
     * 查询物品当前内容状态
     *
     * @param taskId 任务 ID，可为空
     * @return 状态文本
     */
    String contentState(Long taskId);

    /**
     * 取消某业务资源下未完成的任务
     *
     * @param kind       类型
     * @param businessId 业务 ID
     */
    void cancelByBusiness(String kind, Long businessId);

    /**
     * 扫描可执行任务并分派
     */
    void dispatchDueTasks();

    /**
     * 恢复租约过期的运行中任务
     */
    void recoverExpiredTasks();

    /**
     * 创建提醒任务（幂等）
     *
     * @param dedupKey       去重键
     * @param businessId     产检 ID
     * @param businessVersion 产检版本
     * @param payloadJson    受控入参
     * @param executeAt      计划执行时刻
     */
    void scheduleReminder(String dedupKey, Long businessId, Integer businessVersion,
                          String payloadJson, java.time.OffsetDateTime executeAt);

    /**
     * 取消某产检尚未发送的提醒
     *
     * @param checkupId 产检 ID
     */
    void cancelRemindersFor(Long checkupId);

    /**
     * 查询最近一次提醒任务
     *
     * @return 任务摘要，无记录返回 null
     */
    TaskVO latestReminder();
}
