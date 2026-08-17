package com.howe.ai.gateway;

import java.util.List;

import com.howe.ai.contract.ModelDelta;
import com.howe.ai.contract.ProviderErrorCategory;

/** Stub Provider 的可重复响应脚本。usage 为 null 表示 Provider 未上报用量；retryAfterSeconds 非 null 时表示伴随该响应建议的 Retry-After 秒数。 */
public record StubResponse(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String requestId,
                           boolean partial, com.howe.ai.contract.ModelUsage usage, Integer retryAfterSeconds) {
    public StubResponse {
        if (deltas == null) throw new IllegalArgumentException("Stub 增量不能为空");
        deltas = List.copyOf(deltas);
        if (errorCategory != null && !partial && !deltas.isEmpty()) {
            throw new IllegalArgumentException("Stub 失败不能包含增量");
        }
        if (partial && errorCategory == null) {
            throw new IllegalArgumentException("部分 Stub 响应必须包含错误分类");
        }
        if (retryAfterSeconds != null && retryAfterSeconds < 0) throw new IllegalArgumentException("Retry-After 不能为负数");
    }

    public StubResponse(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String requestId,
                        boolean partial, com.howe.ai.contract.ModelUsage usage) {
        this(deltas, errorCategory, requestId, partial, usage, null);
    }

    public StubResponse(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String requestId,
                        boolean partial) {
        this(deltas, errorCategory, requestId, partial, null, null);
    }

    public StubResponse(List<ModelDelta> deltas, ProviderErrorCategory errorCategory, String requestId) {
        this(deltas, errorCategory, requestId, false, null, null);
    }

    public static StubResponse success(String... deltas) {
        return new StubResponse(toDeltas(deltas), null, "stub-request");
    }

    /** 模拟 Provider 上报了用量的场景，用于验证可精算路径。 */
    public static StubResponse successWithUsage(com.howe.ai.contract.ModelUsage usage, String... deltas) {
        return new StubResponse(toDeltas(deltas), null, "stub-request", false, usage);
    }

    private static List<ModelDelta> toDeltas(String... deltas) {
        return java.util.stream.IntStream.range(0, deltas.length)
            .mapToObj(index -> new ModelDelta(deltas[index], index)).toList();
    }

    public static StubResponse failure(ProviderErrorCategory category) {
        return new StubResponse(List.of(), category, "stub-request");
    }

    /** 模拟 Provider 返回 RATE_LIMITED 并通过 Retry-After 建议退避秒数的场景。 */
    public static StubResponse failureWithRetryAfter(ProviderErrorCategory category, int retryAfterSeconds) {
        return new StubResponse(List.of(), category, "stub-request", false, null, retryAfterSeconds);
    }

    public static StubResponse partialFailure(ProviderErrorCategory category, String content) {
        return new StubResponse(List.of(new ModelDelta(content, 0)), category, "stub-request", true);
    }
}
