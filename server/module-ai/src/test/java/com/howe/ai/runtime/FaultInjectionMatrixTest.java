package com.howe.ai.runtime;

import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ProviderErrorCategory;
import com.howe.ai.gateway.ModelRouter;
import com.howe.ai.gateway.StubModelGateway;
import com.howe.ai.gateway.StubResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 7.4 故障注入矩阵：系统性覆盖 Stub Provider 的全部错误分类及其路由/fallback 决策。
 *
 * <p>验收标准要求「区分有限重试与 fallback」「策略错误直接失败不盲目切换」。
 * 本测试把每个 ProviderErrorCategory 都跑一遍，断言瞬态错误可 fallback、策略错误直接失败。
 * 与既有 RuntimeBehaviorTest 的单点验证互补：这里给出完整的决策表。</p>
 */
class FaultInjectionMatrixTest {
    private static final ModelRequest REQUEST = new ModelRequest("stub-model", "prompt", Map.of());

    /** 瞬态错误：允许 fallback 到健康渠道。 */
    @ParameterizedTest
    @EnumSource(value = ProviderErrorCategory.class, names = {"NETWORK_TIMEOUT", "RATE_LIMITED", "SERVER_ERROR"})
    void transientErrorsFallBackToHealthyChannel(ProviderErrorCategory category) {
        StubModelGateway primary = new StubModelGateway();
        primary.enqueue(StubResponse.failure(category));
        primary.enqueue(StubResponse.failure(category));
        primary.enqueue(StubResponse.failure(category));
        StubModelGateway healthy = new StubModelGateway();
        healthy.enqueue(StubResponse.success("ok"));

        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("primary", 1, primary),
            new ModelRouter.RouteTarget("healthy", 2, healthy)));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals("healthy", outcome.targetKey(), category + " 是瞬态错误，必须能 fallback");
        assertTrue(outcome.fallbackAttempts() >= 1);
    }

    /** 策略错误：直接失败，绝不盲目切换到备用渠道。 */
    @ParameterizedTest
    @EnumSource(value = ProviderErrorCategory.class, names = {
        "AUTHENTICATION", "INSUFFICIENT_BALANCE", "INVALID_REQUEST_SCHEMA",
        "CONTEXT_LIMIT", "PERMISSION_DENIED", "SAFETY_REJECTED"})
    void policyErrorsDoNotFallback(ProviderErrorCategory category) {
        StubModelGateway primary = new StubModelGateway();
        primary.enqueue(StubResponse.failure(category));
        StubModelGateway backup = new StubModelGateway();
        backup.enqueue(StubResponse.success("must-not-run"));

        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("primary", 1, primary),
            new ModelRouter.RouteTarget("backup", 2, backup)));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals(category, outcome.result().errorCategory(), category + " 必须原样返回");
        assertEquals(0, outcome.fallbackAttempts(), category + " 是策略错误，不得 fallback");
        assertNotEquals("backup", outcome.targetKey(), "策略错误不得切到备用渠道");
        assertTrue(backup.remaining().contains("must-not-run"), "备用渠道不得被调用");
    }

    /** 上下文超限属于策略错误：不重试、不 fallback、不降级到更小上下文模型。 */
    @Test
    void contextLimitIsTerminalAndDoesNotDowngrade() {
        StubModelGateway primary = new StubModelGateway();
        primary.enqueue(StubResponse.failure(ProviderErrorCategory.CONTEXT_LIMIT));
        StubModelGateway smallerContext = new StubModelGateway();
        smallerContext.enqueue(StubResponse.success("must-not-run"));

        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("primary", 1, primary),
            new ModelRouter.RouteTarget("smaller", 2, smallerContext)));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals(ProviderErrorCategory.CONTEXT_LIMIT, outcome.result().errorCategory());
        assertEquals(0, outcome.fallbackAttempts(), "上下文超限不得降级到其它渠道");
    }
}
