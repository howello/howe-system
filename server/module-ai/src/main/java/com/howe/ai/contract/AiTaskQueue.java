package com.howe.ai.contract;

/** AI Run 任务队列的稳定边界。 */
public interface AiTaskQueue {
    void enqueue(String runId);
    void resume(String runId);
    void cancel(String runId);
}
