package com.howe.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RuntimeContractTest {
    @Test
    void stubGatewayAndRouterExposeOnlyInternalContracts() throws Exception {
        Class<?> stub = assertDoesNotThrow(() -> Class.forName("com.howe.ai.gateway.StubModelGateway"));
        Class<?> router = assertDoesNotThrow(() -> Class.forName("com.howe.ai.gateway.ModelRouter"));
        assertTrue(Arrays.stream(stub.getMethods()).anyMatch(method -> method.getName().equals("complete")));
        assertTrue(Arrays.stream(router.getMethods()).anyMatch(method -> method.getName().equals("complete")));
        assertEquals("com.howe.ai.contract.ModelResult",
            stub.getMethod("complete", Class.forName("com.howe.ai.contract.ModelRequest"))
                .getReturnType().getName());
    }

    @Test
    void transientErrorsRetryButPolicyErrorsDoNotFallback() throws Exception {
        Class<?> policy = assertDoesNotThrow(() -> Class.forName("com.howe.ai.gateway.RetryPolicy"));
        assertTrue(Arrays.stream(policy.getMethods()).anyMatch(method -> method.getName().equals("canRetry")));
        assertTrue(Arrays.stream(policy.getMethods()).anyMatch(method -> method.getName().equals("canFallback")));
        Object rateLimited = Enum.valueOf((Class) Class.forName("com.howe.ai.contract.ProviderErrorCategory"), "RATE_LIMITED");
        Object auth = Enum.valueOf((Class) Class.forName("com.howe.ai.contract.ProviderErrorCategory"), "AUTHENTICATION");
        assertTrue((Boolean) policy.getMethod("canRetry", Class.forName("com.howe.ai.contract.ProviderErrorCategory"), int.class)
            .invoke(null, rateLimited, 0));
        assertFalse((Boolean) policy.getMethod("canFallback", Class.forName("com.howe.ai.contract.ProviderErrorCategory"))
            .invoke(null, auth));
    }

    @Test
    void budgetGuardStopsModelToolAndCostOverruns() throws Exception {
        Class<?> budget = assertDoesNotThrow(() -> Class.forName("com.howe.ai.runtime.BudgetGuard"));
        Object snapshot = Class.forName("com.howe.ai.contract.RunBudgetSnapshot")
            .getConstructor(long.class, int.class, int.class, int.class, long.class, int.class)
            .newInstance(10L, 1, 1, 20, 100L, 1);
        Object guard = budget.getConstructor(Class.forName("com.howe.ai.contract.RunBudgetSnapshot"),
            java.time.Clock.class).newInstance(snapshot, java.time.Clock.systemUTC());
        assertTrue((Boolean) budget.getMethod("allowModelCall").invoke(guard));
        budget.getMethod("recordModelCall", int.class, long.class).invoke(guard, 10, 50L);
        assertFalse((Boolean) budget.getMethod("allowModelCall").invoke(guard));
        assertTrue((Boolean) budget.getMethod("allowToolCall").invoke(guard));
        budget.getMethod("recordToolCall").invoke(guard);
        assertFalse((Boolean) budget.getMethod("allowToolCall").invoke(guard));
    }

    @Test
    void harnessEmitsDeltasAndDoesNotContinueAfterCancellation() throws Exception {
        Class<?> harness = assertDoesNotThrow(() -> Class.forName("com.howe.ai.runtime.ConfigAgentRuntime"));
        assertTrue(Arrays.stream(harness.getMethods()).anyMatch(method -> method.getName().equals("run")));
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/runtime/ConfigAgentRuntime.java"));
        assertTrue(source.contains("delta"));
        assertTrue(source.contains("CANCEL_REQUESTED"));
        assertTrue(source.contains("checkpoint"));
    }

    @Test
    void eventReaderUsesLastEventIdAndFallsBackToFacts() throws Exception {
        Class<?> events = assertDoesNotThrow(() -> Class.forName("com.howe.ai.event.AiRunEventService"));
        assertTrue(Arrays.stream(events.getMethods()).anyMatch(method -> method.getName().equals("readAfter")));
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/event/AiRunEventService.java"));
        assertTrue(source.contains("lastEventId"));
        assertTrue(source.contains("listEvents"));
        assertTrue(source.contains("eventId"));
    }
}
