package com.howe.ai.capability;

import com.howe.ai.route.AiRouteTarget;

/**
 * 一次针对具体目标的模型调用
 *
 * @param <T> 调用结果类型
 * @author howe
 */
@FunctionalInterface
public interface AiCall<T>
{
    /**
     * 执行调用
     *
     * @param target 目标（渠道 + 模型）
     * @return 调用结果
     * @throws Exception 厂商或网络异常，由执行模板统一分类
     */
    T call(AiRouteTarget target) throws Exception;
}
