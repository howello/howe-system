package com.howe.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.ModelGateway;
import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ModelResult;
import com.howe.ai.contract.ProviderErrorCategory;

class RouterCircuitTest {
    @Test
    void routerSkipsOpenCircuitAndUsesHealthyTarget() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        CircuitBreaker open = new CircuitBreaker(1, Duration.ofMinutes(1), Clock.fixed(now, ZoneOffset.UTC));
        open.recordFailure();
        StubModelGateway healthy = new StubModelGateway();
        healthy.enqueue(StubResponse.success("ok"));
        ModelRouter router = new ModelRouter(List.of(
            new ModelRouter.RouteTarget("open", 1, new CircuitModelGateway(open, new StubModelGateway())),
            new ModelRouter.RouteTarget("healthy", 2, healthy)));

        ModelRouter.RoutingOutcome outcome = router.route(new ModelRequest("stub", "prompt", Map.of()),
            ModelRouter.RoutingLimits.defaults());

        assertEquals("ok", outcome.result().deltas().get(0).content());
        assertTrue(outcome.fallbackAttempts() >= 1);
    }

    private record CircuitModelGateway(CircuitBreaker breaker, ModelGateway delegate) implements ModelGateway {
        @Override
        public com.howe.ai.contract.ModelStreamResult stream(ModelRequest request) {
            if (!breaker.allowRequest()) {
                return new com.howe.ai.contract.ModelStreamResult(List.of(), ProviderErrorCategory.SERVER_ERROR, "open");
            }
            var result = delegate.stream(request);
            if (result.success()) breaker.recordSuccess(); else breaker.recordFailure();
            return result;
        }
    }
}
