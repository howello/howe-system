package com.howe.ai.gateway;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.howe.ai.contract.ModelGateway;
import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ModelResult;
import com.howe.ai.contract.ModelStreamResult;
import com.howe.ai.contract.ProviderErrorCategory;

/**
 * 按 priority 选择模型，并将重试与 fallback 分开计数。
 *
 * <p>路由本身不持有任何可变状态：计数随每次调用返回，因此同一个路由实例可以被并发使用。
 * 熔断状态属于渠道而非本次调用，保存在 {@link RouteTarget} 持有的熔断器里。</p>
 */
public class ModelRouter implements ModelGateway {
    private final List<RouteTarget> targets;

    public ModelRouter(List<RouteTarget> targets) {
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("路由不能为空");
        this.targets = targets.stream().sorted(Comparator.comparingInt(RouteTarget::priority)).toList();
    }

    @Override
    public ModelStreamResult stream(ModelRequest request) {
        return route(request, RoutingLimits.defaults()).result();
    }

    @Override
    public ModelResult complete(ModelRequest request) {
        ModelStreamResult result = stream(request);
        if (!result.success()) return new ModelResult(null, result.errorCategory(), result.providerRequestId());
        return ModelResult.success(result.deltas().stream().map(delta -> delta.content()).reduce("", String::concat),
            result.providerRequestId());
    }

    /** 按优先级选路，跳过已停用与熔断打开的渠道，并在预算允许范围内重试与 fallback。 */
    public RoutingOutcome route(ModelRequest request, RoutingLimits limits) {
        Objects.requireNonNull(request, "模型请求不能为空");
        Objects.requireNonNull(limits, "路由预算不能为空");
        ModelStreamResult last = null;
        String lastKey = null;
        int fallbackAttempts = 0;
        int skipped = 0;

        for (RouteTarget target : targets) {
            if (!target.enabled()) {
                skipped++;
                continue;
            }
            ModelStreamResult result = null;
            boolean attempted = false;
            for (int attempt = 0; attempt < limits.maxAttemptsPerTarget(); attempt++) {
                // 熔断打开时直接放弃该渠道，不占用 Provider 配额也不计为失败。
                if (!target.allowRequest()) break;
                attempted = true;
                result = target.gateway().stream(request);
                if (result.success()) {
                    target.recordSuccess();
                    return new RoutingOutcome(result, fallbackAttempts, target.key(), skipped);
                }
                target.recordFailure();
                // 部分断流按失败计入熔断，但不再重试，也不拼接备用内容。
                if (result.partial()) return new RoutingOutcome(result, fallbackAttempts, target.key(), skipped);
                // Provider 通过 Retry-After 建议的退避超过路由愿意等待的上限时，尊重建议：停止该渠道重试，
                // 由外层 fallback 切换到下一个可用渠道，而不是无脑重试浪费 Provider 配额。
                if (result.retryAfterAdvised()
                    && result.retryAfterSeconds() > limits.maxRetryAfterSeconds()) break;
                if (!RetryPolicy.canRetry(result.errorCategory(), attempt)) break;
            }
            if (!attempted) {
                skipped++;
                continue;
            }
            last = result;
            lastKey = target.key();
            if (!RetryPolicy.canFallback(result.errorCategory())) {
                return new RoutingOutcome(result, fallbackAttempts, lastKey, skipped);
            }
            if (fallbackAttempts >= limits.maxFallbackAttempts()) {
                return new RoutingOutcome(result, fallbackAttempts, lastKey, skipped);
            }
            fallbackAttempts++;
        }
        if (last == null) {
            last = new ModelStreamResult(List.of(), ProviderErrorCategory.UNKNOWN, "no-route-available");
        }
        return new RoutingOutcome(last, fallbackAttempts, lastKey, skipped);
    }

    /** 单次选路的结果与统计，随调用返回而不落到实例字段。 */
    public record RoutingOutcome(ModelStreamResult result, int fallbackAttempts, String targetKey, int skippedTargets) {
    }

    /** 由 Run 预算派生的路由上限。maxRetryAfterSeconds 是单次重试愿意等待 Provider 建议 Retry-After 的上限秒数。 */
    public record RoutingLimits(int maxAttemptsPerTarget, int maxFallbackAttempts, int maxRetryAfterSeconds) {
        public RoutingLimits {
            if (maxAttemptsPerTarget < 1) throw new IllegalArgumentException("单渠道尝试次数必须为正数");
            if (maxFallbackAttempts < 0) throw new IllegalArgumentException("fallback 次数不能为负数");
            if (maxRetryAfterSeconds < 0) throw new IllegalArgumentException("Retry-After 等待上限不能为负数");
        }

        public RoutingLimits(int maxAttemptsPerTarget, int maxFallbackAttempts) {
            this(maxAttemptsPerTarget, maxFallbackAttempts, 5);
        }

        public static RoutingLimits defaults() {
            return new RoutingLimits(3, Integer.MAX_VALUE, 5);
        }
    }

    public record RouteTarget(String key, int priority, ModelGateway gateway, boolean enabled, CircuitBreaker breaker) {
        public RouteTarget {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("路由标识不能为空");
            if (priority < 0) throw new IllegalArgumentException("路由优先级不能为负数");
            Objects.requireNonNull(gateway, "模型网关不能为空");
        }

        public RouteTarget(String key, int priority, ModelGateway gateway) {
            this(key, priority, gateway, true, null);
        }

        boolean allowRequest() {
            return breaker == null || breaker.allowRequest();
        }

        void recordSuccess() {
            if (breaker != null) breaker.recordSuccess();
        }

        void recordFailure() {
            if (breaker != null) breaker.recordFailure();
        }
    }
}
