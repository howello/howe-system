package com.howe.ai.contract;

import java.util.List;

/**
 * 模型流式调用结果；失败时不返回部分成功内容。usage 为 null 表示 Provider 未上报用量。
 *
 * <p>retryAfterSeconds 为 null 表示 Provider 未建议退避；非 null 表示 Provider 通过 Retry-After
 * 建议的退避秒数，仅在 RATE_LIMITED 等瞬态错误时有意义。Router 据此决定是否在该渠道上继续重试：
 * 建议退避超过路由愿意等待的上限时跳过该渠道直接 fallback，而不是无脑重试浪费配额。</p>
 */
public record ModelStreamResult(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String providerRequestId,
                                boolean partial, ModelUsage usage, Integer retryAfterSeconds) {
    public ModelStreamResult {
        if (deltas == null) throw new IllegalArgumentException("模型增量不能为空");
        deltas = List.copyOf(deltas);
        if (errorCategory != null && !partial && !deltas.isEmpty()) throw new IllegalArgumentException("失败结果不能包含增量");
        if (partial && errorCategory == null) throw new IllegalArgumentException("部分流必须包含错误分类");
        if (retryAfterSeconds != null && retryAfterSeconds < 0) throw new IllegalArgumentException("Retry-After 不能为负数");
    }

    public ModelStreamResult(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String providerRequestId,
                             boolean partial, ModelUsage usage) {
        this(deltas, errorCategory, providerRequestId, partial, usage, null);
    }

    public ModelStreamResult(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String providerRequestId,
                             boolean partial) {
        this(deltas, errorCategory, providerRequestId, partial, null, null);
    }

    public ModelStreamResult(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String providerRequestId) {
        this(deltas, errorCategory, providerRequestId, false, null, null);
    }

    public boolean success() {
        return errorCategory == null;
    }

    /** Provider 是否上报了可用于精确计费的用量。 */
    public boolean usageReported() {
        return usage != null;
    }

    /** 是否携带了 Provider 建议的退避秒数（通常伴随 RATE_LIMITED）。 */
    public boolean retryAfterAdvised() {
        return retryAfterSeconds != null;
    }
}
