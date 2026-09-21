package com.howe.ai.provider;

/**
 * 对话参数
 *
 * <p>三个可覆盖的采样参数，由「全局默认 &lt; 场景 params &lt; 请求显式字段」合并后得到。</p>
 *
 * @param temperature 采样温度
 * @param maxTokens   最大输出 token 数
 * @param topP        核采样
 * @author howe
 */
public record ChatSpec(Double temperature, Integer maxTokens, Double topP)
{
}
