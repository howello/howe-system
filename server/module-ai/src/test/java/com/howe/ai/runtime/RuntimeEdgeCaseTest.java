package com.howe.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.AiRunEvent;
import com.howe.ai.contract.ModelGateway;
import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ModelStreamResult;
import com.howe.ai.contract.ProviderErrorCategory;
import com.howe.ai.contract.RunBudgetSnapshot;
import com.howe.ai.event.AiEventStore;
import com.howe.ai.event.AiRunEventService;
import com.howe.ai.event.RecordingEventEmitter;
import com.howe.ai.gateway.CircuitBreaker;
import com.howe.ai.gateway.ModelRouter;
import com.howe.ai.gateway.StubModelGateway;
import com.howe.ai.gateway.StubResponse;

class RuntimeEdgeCaseTest {
    private static final ModelRequest REQUEST = new ModelRequest("stub-model", "hello", Map.of());

    @Test
    void modelGatewayExposesStreamingWithoutSpringAiTypes() throws Exception {
        assertEquals(ModelStreamResult.class, ModelGateway.class.getMethod("stream", ModelRequest.class).getReturnType());
    }

    @Test
    void partialStreamEndsAsRecoverableFailureWithoutFallbackContent() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.partialFailure(ProviderErrorCategory.NETWORK_TIMEOUT, "part"));
        ModelRouter router = new ModelRouter(List.of(new ModelRouter.RouteTarget("stub", 1, gateway)));
        ModelStreamResult result = router.stream(REQUEST);

        assertTrue(result.partial());
        assertEquals(ProviderErrorCategory.NETWORK_TIMEOUT, result.errorCategory());
        assertEquals(List.of("part"), result.deltas().stream().map(delta -> delta.content()).toList());
    }

    @Test
    void runtimeRejectsOutputAndCostBudgetBeforeCompletion() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("123456"));
        ConfigAgentRuntime runtime = new ConfigAgentRuntime(gateway, new RecordingEventEmitter());

        RuntimeResult result = runtime.run("run-1", REQUEST,
            new RunBudgetSnapshot(30, 1, 1, 3, 2, 0), () -> false);

        assertFalse(result.success());
        assertEquals(ProviderErrorCategory.UNKNOWN, result.errorCategory());
    }

    @Test
    void runtimeCountsOneModelCallForMultipleDeltas() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("a", "b"));
        ConfigAgentRuntime runtime = new ConfigAgentRuntime(gateway, new RecordingEventEmitter());

        RuntimeResult result = runtime.run("run-1", REQUEST,
            new RunBudgetSnapshot(30, 1, 10, 20, 100, 0), () -> false);

        assertTrue(result.success());
        assertEquals("ab", result.content());
    }

    @Test
    void circuitBreakerTransitionsToHalfOpenAfterCooldown() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofSeconds(10), Clock.fixed(now, ZoneOffset.UTC));
        breaker.recordFailure();
        breaker.recordFailure();
        assertFalse(breaker.allowRequest());
        breaker = breaker.withClock(Clock.fixed(now.plusSeconds(11), ZoneOffset.UTC));
        assertTrue(breaker.allowRequest());
        breaker.recordSuccess();
        assertTrue(breaker.allowRequest());
    }

    @Test
    void eventServiceReturnsFactsWhenRealtimeBridgeFails() {
        AiRunEvent event = new AiRunEvent("run-1", "run-1:1", 1, "run.started",
            Instant.EPOCH, "idem-1", "{}");
        AiEventStore facts = (runId, cursor) -> List.of(event);
        AiEventStore brokenRealtime = (runId, cursor) -> { throw new IllegalStateException("redis down"); };
        AiRunEventService service = new AiRunEventService(facts, brokenRealtime);

        var result = service.readAfterWithStatus("run-1", null);

        assertTrue(result.degraded());
        assertEquals(List.of(event), result.events());
    }
}
