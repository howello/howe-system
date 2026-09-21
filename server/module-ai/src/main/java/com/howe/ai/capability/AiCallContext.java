package com.howe.ai.capability;

/**
 * 调用记录中与本次请求相关的通用信息
 *
 * @param traceId    链路 ID，一次业务调用一个，降级产生的多条记录共用
 * @param scene      场景标识
 * @param capability 能力类型
 * @param digest     脱敏后的入参摘要
 * @param operator   触发人
 * @author howe
 */
public record AiCallContext(String traceId, String scene, AiCapability capability, String digest, String operator)
{
}
