package com.howe.meal.service;

/**
 * 提交当前业务事务后执行副作用回调。
 */
public interface ITransactionCommitExecutor {

    /**
     * 事务提交成功后执行任务；调用时没有活动事务则立即执行。
     *
     * @param callback 待执行任务
     */
    void afterCommit(Runnable callback);
}
