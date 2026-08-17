package com.howe.ai.contract;

/**
 * Provider 上报的用量。
 *
 * <p>只承载 Provider 真实返回的数字。Provider 未上报时不构造本对象，而是保持 {@code null}，
 * 由调用方明确记为「不可精算」——把缺失用量折算成字符数或零成本都会让预算与成本审计失真。</p>
 */
public record ModelUsage(int promptTokens, int completionTokens) {
    public ModelUsage {
        if (promptTokens < 0 || completionTokens < 0) throw new IllegalArgumentException("用量不能为负数");
    }
}
