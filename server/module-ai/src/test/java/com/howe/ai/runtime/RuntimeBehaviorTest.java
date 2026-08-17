package com.howe.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.AiRunEvent;
import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ModelResult;
import com.howe.ai.contract.ModelStreamResult;
import com.howe.ai.contract.ProviderErrorCategory;
import com.howe.ai.contract.RunBudgetSnapshot;
import com.howe.ai.event.AiEventStore;
import com.howe.ai.event.AiRunEventService;
import com.howe.ai.event.RecordingEventEmitter;
import com.howe.ai.gateway.ModelRouter;
import com.howe.ai.gateway.StubModelGateway;
import com.howe.ai.gateway.StubResponse;

class RuntimeBehaviorTest {
    private static final ModelRequest REQUEST = new ModelRequest("stub-model", "hello", Map.of());

    @Test
    void stubProviderReturnsConfiguredStreamingDeltas() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("hel", "lo"));

        ModelStreamResult result = gateway.stream(REQUEST);

        assertEquals(List.of("hel", "lo"), result.deltas().stream().map(delta -> delta.content()).toList());
        assertTrue(result.success());
    }

    @Test
    void routerFallsBackOnlyAfterTransientFailure() {
        StubModelGateway primary = new StubModelGateway();
        primary.enqueue(StubResponse.failure(ProviderErrorCategory.SERVER_ERROR));
        primary.enqueue(StubResponse.failure(ProviderErrorCategory.SERVER_ERROR));
        primary.enqueue(StubResponse.failure(ProviderErrorCategory.SERVER_ERROR));
        StubModelGateway fallback = new StubModelGateway();
        fallback.enqueue(StubResponse.success("fallback"));
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("primary", 1, primary),
            new ModelRouter.RouteTarget("fallback", 2, fallback)));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals("fallback", outcome.result().deltas().get(0).content());
        assertEquals(1, outcome.fallbackAttempts());
    }

    @Test
    void routerDoesNotFallbackAuthenticationFailure() {
        StubModelGateway primary = new StubModelGateway();
        primary.enqueue(StubResponse.failure(ProviderErrorCategory.AUTHENTICATION));
        StubModelGateway fallback = new StubModelGateway();
        fallback.enqueue(StubResponse.success("must-not-run"));
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("primary", 1, primary),
            new ModelRouter.RouteTarget("fallback", 2, fallback)));

        ModelRouter.RoutingOutcome outcome = router.route(REQUEST, ModelRouter.RoutingLimits.defaults());

        assertEquals(ProviderErrorCategory.AUTHENTICATION, outcome.result().errorCategory());
        assertEquals(0, outcome.fallbackAttempts());
    }

    @Test
    void runtimeEmitsOrderedDeltasAndCompletion() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("a", "b"));
        RecordingEventEmitter emitter = new RecordingEventEmitter();
        ConfigAgentRuntime runtime = new ConfigAgentRuntime(gateway, emitter);

        RuntimeResult result = runtime.run("run-1", REQUEST,
            new RunBudgetSnapshot(30, 2, 1, 20, 100, 1), () -> false);

        assertEquals("ab", result.content());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), emitter.events().stream().map(AiRunEvent::sequence).toList());
        assertEquals(List.of("run.started", "run.checkpoint", "message.delta", "message.delta",
            "run.usage", "run.completed"), emitter.events().stream().map(AiRunEvent::type).toList());
    }

    @Test
    void runtimeStopsBeforeModelWhenCancelled() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("must-not-run"));
        ConfigAgentRuntime runtime = new ConfigAgentRuntime(gateway, new RecordingEventEmitter());

        RuntimeResult result = runtime.run("run-1", REQUEST,
            new RunBudgetSnapshot(30, 2, 1, 20, 100, 1), () -> true);

        assertEquals(ProviderErrorCategory.PERMISSION_DENIED, result.errorCategory());
        assertFalse(result.success());
    }

    @Test
    void eventServiceDeduplicatesFactsAndRealtimeEventsAfterCursor() {
        AiRunEvent first = event("run-1", 1, "run.started");
        AiRunEvent second = event("run-1", 2, "message.delta");
        AiEventStore facts = (runId, lastSequence) -> List.of(first, second);
        AiEventStore realtime = (runId, lastSequence) -> List.of(second, event("run-1", 3, "run.completed"));
        AiRunEventService service = new AiRunEventService(facts, realtime);

        List<AiRunEvent> events = service.readAfter("run-1", "run-1:1");

        assertEquals(List.of(2L, 3L), events.stream().map(AiRunEvent::sequence).toList());
    }

    private static AiRunEvent event(String runId, long sequence, String type) {
        return new AiRunEvent(runId, runId + ":" + sequence, sequence, type,
            Instant.ofEpochSecond(sequence), "idem-" + sequence, "{}");
    }
}
