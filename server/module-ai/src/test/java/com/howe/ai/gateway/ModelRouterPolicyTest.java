package com.howe.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ProviderErrorCategory;

/**
 * 路由的熔断、启停与预算约束。
 *
 * <p>此前 Router 把 fallback 次数存在实例字段里并在每次调用开头重置，
 * 并发调用会互相覆盖计数；熔断器、渠道启停和预算也完全没有接入选路。</p>
 */
class ModelRouterPolicyTest {
    private static final ModelRequest REQUEST = new ModelRequest("stub-model", "prompt", Map.of());

    @Test
    void routerKeepsNoMutableInstanceStateAcrossCalls() {
        List<String> mutable = java.util.Arrays.stream(ModelRouter.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .filter(field -> !Modifier.isFinal(field.getModifiers()))
            .map(java.lang.reflect.Field::getName).toList();

        assertEquals(List.of(), mutable, "路由不得持有可变实例状态，否则并发调用会互相覆盖");
    }

    @Test
    void eachRoutingReportsItsOwnFallbackCount() {
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("primary", 1, failing(ProviderErrorCategory.SERVER_ERROR, 3)),
            new ModelRouter.RouteTarget("healthy", 2, succeeding("ok"))));

        ModelRouter.RoutingOutcome fellBack = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals(1, fellBack.fallbackAttempts());
        assertEquals("healthy", fellBack.targetKey());
    }

    @Test
    void disabledTargetIsSkippedEntirely() {
        StubModelGateway disabled = succeeding("不应被调用");
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("disabled", 1, disabled, false, null),
            new ModelRouter.RouteTarget("healthy", 2, succeeding("ok"))));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals("healthy", outcome.targetKey());
        assertEquals(List.of("不应被调用"), disabled.remaining(), "停用渠道不得被调用");
    }

    @Test
    void openCircuitTargetIsSkippedWithoutCallingProvider() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        CircuitBreaker open = new CircuitBreaker(1, Duration.ofMinutes(1), Clock.fixed(now, ZoneOffset.UTC));
        open.recordFailure();
        StubModelGateway behindBreaker = succeeding("不应被调用");
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("open", 1, behindBreaker, true, open),
            new ModelRouter.RouteTarget("healthy", 2, succeeding("ok"))));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals("healthy", outcome.targetKey());
        assertEquals(List.of("不应被调用"), behindBreaker.remaining(), "熔断打开时不得真的发起调用");
    }

    @Test
    void providerFailureTripsTheCircuitForLaterRoutings() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMinutes(1), Clock.fixed(now, ZoneOffset.UTC));
        StubModelGateway flaky = failing(ProviderErrorCategory.SERVER_ERROR, 3);
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("flaky", 1, flaky, true, breaker),
            new ModelRouter.RouteTarget("healthy", 2, succeeding("ok"))));

        router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertTrue(!breaker.allowRequest(), "连续失败后熔断器必须打开");
    }

    @Test
    void fallbackStopsWhenBudgetForbidsFurtherAttempts() {
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("primary", 1, failing(ProviderErrorCategory.SERVER_ERROR, 3)),
            new ModelRouter.RouteTarget("secondary", 2, succeeding("不应被调用"))));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, new ModelRouter.RoutingLimits(3, 0));

        assertEquals(ProviderErrorCategory.SERVER_ERROR, outcome.result().errorCategory());
        assertEquals(0, outcome.fallbackAttempts(), "预算禁止 fallback 时不得切换渠道");
    }

    @Test
    void retryAfterBeyondWaitBudgetAbandonsTargetAndFallsBack() {
        // 主渠道 429 并建议退避 60s，超过默认愿意等待的 5s 上限：尊重 Provider，停止该渠道重试，切到备用渠道。
        StubModelGateway rateLimited = new StubModelGateway();
        rateLimited.enqueue(StubResponse.failureWithRetryAfter(ProviderErrorCategory.RATE_LIMITED, 60));
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("limited", 1, rateLimited),
            new ModelRouter.RouteTarget("healthy", 2, succeeding("ok"))));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals("healthy", outcome.targetKey(), "Retry-After 超限时必须 fallback 到健康渠道");
        assertEquals(1, outcome.fallbackAttempts());
    }

    @Test
    void retryAfterWithinWaitBudgetKeepsRetryingTheSameTarget() {
        // 主渠道 429 建议退避 2s，在愿意等待的 5s 内：不放弃该渠道，仍按 canRetry 在同渠道重试直到成功。
        StubModelGateway rateLimited = new StubModelGateway();
        rateLimited.enqueue(StubResponse.failureWithRetryAfter(ProviderErrorCategory.RATE_LIMITED, 2));
        rateLimited.enqueue(StubResponse.success("ok"));
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("limited", 1, rateLimited),
            new ModelRouter.RouteTarget("healthy", 2, succeeding("不应被调用"))));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals("limited", outcome.targetKey(), "Retry-After 在等待上限内时不应 fallback");
        assertEquals(0, outcome.fallbackAttempts());
    }

    @Test
    void retryAfterIsPropagatedThroughStubGatewayToContract() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.failureWithRetryAfter(ProviderErrorCategory.RATE_LIMITED, 30));

        com.howe.ai.contract.ModelStreamResult result = gateway.stream(REQUEST);

        assertEquals(ProviderErrorCategory.RATE_LIMITED, result.errorCategory());
        assertTrue(result.retryAfterAdvised(), "Stub 的 Retry-After 必须透传到 ModelStreamResult");
        assertEquals(30, result.retryAfterSeconds());
    }

    private static StubModelGateway succeeding(String content) {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success(content));
        return gateway;
    }

    private static StubModelGateway failing(ProviderErrorCategory category, int times) {
        StubModelGateway gateway = new StubModelGateway();
        for (int index = 0; index < times; index++) gateway.enqueue(StubResponse.failure(category));
        return gateway;
    }
}
