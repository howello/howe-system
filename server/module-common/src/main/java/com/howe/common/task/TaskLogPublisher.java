package com.howe.common.task;

/**
 * 调度任务步骤日志发布接口。
 *
 * <p>实现留在 Quartz 模块，业务任务只依赖这个公共契约。</p>
 */
public interface TaskLogPublisher
{
    /**
     * 开始一条步骤记录。
     *
     * @param record 初始步骤记录，状态通常为 RUNNING
     * @return 明细编号
     */
    Long begin(TaskLogRecord record);

    /**
     * 发布一条步骤过程信息，不改变步骤生命周期。
     *
     * @param record 过程信息记录，状态通常为 INFO 或 SKIPPED
     */
    void info(TaskLogRecord record);

    /**
     * 更新步骤终态。
     *
     * @param detailId 明细编号
     * @param record 终态步骤记录
     */
    void finish(Long detailId, TaskLogRecord record);
}
