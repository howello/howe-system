package com.howe.ai.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.howe.ai.contract.AiToolProvider;
import com.howe.ai.contract.ToolAllowlistSnapshot;
import com.howe.ai.contract.ToolDefinition;
import com.howe.ai.contract.ToolRequest;
import com.howe.ai.contract.ToolResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 冻结下沉到 {@code module-ai-common} 的 Tool 契约：字段、消息校验规则与 Spring AI 隔离。
 *
 * <p>这些契约是 {@code module-blog} 等 Tool 提供方唯一可依赖的 AI 域接口，不允许反向依赖
 * {@code module-ai} 实现，因此单独在此验证其稳定性和纯 JDK 依赖。</p>
 */
class ToolContractSchemaTest {

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
        assertThrows(IllegalArgumentException.class, () -> new ToolDefinition("name", " ", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new ToolDefinition("name", "desc", " "));
        assertThrows(IllegalArgumentException.class, () -> new ToolRequest("tool", " "));
        assertThrows(IllegalArgumentException.class, () -> ToolResult.success(null));
        assertThrows(IllegalArgumentException.class, () -> ToolResult.failure("", "message"));
        assertThrows(IllegalArgumentException.class, () -> ToolResult.failure("code", " "));

        ToolResult ok = ToolResult.success("正常内容");
        assertTrue(ok.success());
        assertEquals("正常内容", ok.content());
        assertNull(ok.errorMessage());
        ToolResult bad = ToolResult.failure("NOT_FOUND", "未找到");
        assertFalse(bad.success());
        assertNull(bad.content());
        assertTrue(bad.errorMessage().contains("NOT_FOUND"));
    }

    @Test
    void toolAllowlistSnapshotDefendsEmptyAndBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> new ToolAllowlistSnapshot(null));
        assertThrows(IllegalArgumentException.class, () -> new ToolAllowlistSnapshot(Set.of("", "x")));
        assertThrows(IllegalArgumentException.class, () -> new ToolAllowlistSnapshot(Set.of(" ", "x")));
        assertEquals(Set.of("search"), new ToolAllowlistSnapshot(Set.of("search")).toolNames());
        // 不可变副本：修改入参集合不影响快照
        Set<String> source = new java.util.HashSet<>(Set.of("a"));
        ToolAllowlistSnapshot snapshot = new ToolAllowlistSnapshot(source);
        source.add("b");
        assertEquals(Set.of("a"), snapshot.toolNames());
    }

    @Test
    void toolContractsAvoidSpringAiTypesEverywhere() {
        for (Class<?> contract : new Class<?>[]{
            AiToolProvider.class, ToolDefinition.class, ToolRequest.class,
            ToolResult.class, ToolAllowlistSnapshot.class}) {
            assertFalse(contract.getPackageName().contains("springframework.ai"));
            for (java.lang.reflect.Method method : contract.getDeclaredMethods()) {
                assertFalse(method.getReturnType().getTypeName().contains("org.springframework.ai"));
                for (Class<?> param : method.getParameterTypes()) {
                    assertFalse(param.getTypeName().contains("org.springframework.ai"));
                }
            }
        }
    }
}