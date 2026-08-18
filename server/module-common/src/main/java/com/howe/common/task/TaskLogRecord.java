package com.howe.common.task;

import java.util.Date;

/**
 * 定时任务步骤日志记录。
 *
 * @param jobLogId 调度日志编号
 * @param stepNo 步骤序号
 * @param stepName 步骤名称
 * @param status 步骤状态
 * @param message 步骤消息
 * @param errorInfo 脱敏后的异常信息
 * @param startTime 步骤开始时间
 * @param endTime 步骤结束时间
 * @param durationMs 步骤耗时
 */
public record TaskLogRecord(
        Long jobLogId,
        Integer stepNo,
        String stepName,
        String status,
        String message,
        String errorInfo,
        Date startTime,
        Date endTime,
        Long durationMs)
{
}
