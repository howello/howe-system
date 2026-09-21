package com.howe.ai.capability;

import com.howe.ai.route.AiRouteTarget;

/**
 * 一次成功的模型调用
 *
 * @param value     调用结果
 * @param target    实际生效的目标
 * @param elapsedMs 耗时（毫秒）
 * @param callLogId 调用记录 ID，可能为空
 * @param <T>       结果类型
 * @author howe
 */
public record AiInvocation<T>(T value, AiRouteTarget target, long elapsedMs, Long callLogId)
{
}
