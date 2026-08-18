package com.howe.ai.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractSchemaTest {
    private static final Class<?>[] PUBLIC_CONTRACTS = {
        AiAgentService.class, ModelGateway.class, AiTaskQueue.class, AiToolProvider.class,
        AgentRunRequest.class, AgentRunSnapshot.class, AiRunEvent.class, ModelRequest.class,
        ModelResult.class, ModelPriceSnapshot.class, RunBudgetSnapshot.class,
        ToolAllowlistSnapshot.class, ToolDefinition.class, ToolRequest.class, ToolResult.class
    };

    @Test
    void eventHasStableFieldsAndPositiveSequence() {
        AiRunEvent event = new AiRunEvent("run-1", "event-1", 1L, "run.started",
            Instant.now(), "idem-1", "{}");
        assertEquals(1L, event.sequence());
        assertThrows(IllegalArgumentException.class,
            () -> new AiRunEvent(" ", "e", 1L, "x", Instant.now(), "i", "{}"));
        assertThrows(IllegalArgumentException.class,
            () -> new AiRunEvent("r", "e", 1L, " ", Instant.now(), "i", "{}"));
        assertThrows(IllegalArgumentException.class,
            () -> new AiRunEvent("r", "e", 1L, "x", Instant.now(), " ", "{}"));
        assertThrows(IllegalArgumentException.class,
            () -> new AiRunEvent("r", "e", 1L, "x", Instant.now(), "i", " "));
    }

    @Test
    void providerErrorsCoverAllRequiredCategories() {
        assertEquals(10, ProviderErrorCategory.values().length);
        assertTrue(Arrays.stream(ProviderErrorCategory.values()).map(Enum::name).toList()
            .containsAll(Arrays.asList("NETWORK_TIMEOUT", "RATE_LIMITED", "SERVER_ERROR",
                "AUTHENTICATION", "INSUFFICIENT_BALANCE", "INVALID_REQUEST_SCHEMA",
                "CONTEXT_LIMIT", "PERMISSION_DENIED", "SAFETY_REJECTED", "UNKNOWN")));
    }

    @Test
    void toolProviderUsesTypedContracts() throws Exception {
        assertEquals(java.util.List.class, AiToolProvider.class.getMethod("describeTools").getReturnType());
        assertEquals(ToolResult.class, AiToolProvider.class.getMethod("invoke", ToolRequest.class)
            .getReturnType());
        assertEquals(ToolRequest.class, AiToolProvider.class.getMethod("invoke", ToolRequest.class)
            .getParameterTypes()[0]);
    }

    @Test
    void toolContractsUseJsonStringsAndValidateMessages() {
        assertEquals(String.class, ToolDefinition.class.getRecordComponents()[2].getType());
        assertEquals(String.class, ToolRequest.class.getRecordComponents()[1].getType());
        assertThrows(IllegalArgumentException.class, () -> new ToolDefinition(" ", "description", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new ToolRequest("tool", " "));
        assertThrows(IllegalArgumentException.class, () -> ToolResult.success(null));
        assertThrows(IllegalArgumentException.class, () -> ToolResult.failure("", "message"));
        assertThrows(IllegalArgumentException.class, () -> ToolResult.failure("code", " "));

        Map<String, Object> businessObject = new HashMap<>();
        businessObject.put("secret", new Object());
        assertThrows(AssertionError.class, () -> assertEquals(String.class,
            businessObject.get("secret").getClass()));
    }

    @Test
    void allPublicContractsAvoidSpringAiTypesEverywhere() {
        for (Class<?> contract : PUBLIC_CONTRACTS) {
            assertNoSpringAi(contract.getGenericInterfaces());
            assertNoSpringAi(contract.getGenericSuperclass());
            for (Method method : contract.getDeclaredMethods()) {
                assertNoSpringAi(method.getGenericReturnType());
                assertNoSpringAi(method.getGenericParameterTypes());
                for (Parameter parameter : method.getParameters()) {
                    assertNoSpringAi(parameter.getParameterizedType());
                }
            }
            for (Constructor<?> constructor : contract.getDeclaredConstructors()) {
                assertNoSpringAi(constructor.getGenericParameterTypes());
                for (Parameter parameter : constructor.getParameters()) {
                    assertNoSpringAi(parameter.getParameterizedType());
                }
            }
            for (Field field : contract.getDeclaredFields()) {
                assertNoSpringAi(field.getGenericType());
            }
            RecordComponent[] components = contract.getRecordComponents();
            if (components != null) {
                for (RecordComponent component : components) {
                    assertNoSpringAi(component.getGenericType());
                    assertNoSpringAi(component.getAnnotatedType().getType());
                }
            }
        }
    }

    private static void assertNoSpringAi(Type... types) {
        for (Type type : types) {
            if (type == null) continue;
            assertFalse(type.getTypeName().contains("org.springframework.ai"), type.getTypeName());
            if (type instanceof ParameterizedType parameterized) {
                assertNoSpringAi(parameterized.getActualTypeArguments());
                assertNoSpringAi(parameterized.getRawType());
            } else if (type instanceof GenericArrayType array) {
                assertNoSpringAi(array.getGenericComponentType());
            } else if (type instanceof WildcardType wildcard) {
                assertNoSpringAi(wildcard.getLowerBounds());
                assertNoSpringAi(wildcard.getUpperBounds());
            } else if (type instanceof TypeVariable<?> variable) {
                assertNoSpringAi(variable.getBounds());
            }
        }
    }
}
