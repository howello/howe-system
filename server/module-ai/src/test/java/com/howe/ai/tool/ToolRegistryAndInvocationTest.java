package com.howe.ai.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.AiToolProvider;
import com.howe.ai.contract.ToolDefinition;
import com.howe.ai.contract.ToolRequest;
import com.howe.ai.contract.ToolResult;

/**
 * Tool Registry 与安全调用链测试：自动收集、按名查找、白名单裁决、模型输入不改变拒绝、
 * 入参/输出上限、审计记录。
 */
class ToolRegistryAndInvocationTest {

    @Test
    void registryCollectsAllToolsFromProviders() {
        ToolRegistry registry = new ToolRegistry(List.of(
            provider("alpha", "beta"), provider("gamma")));
        assertEquals(Set.of("alpha", "beta", "gamma"), registry.knownToolNames());
        assertTrue(registry.findProvider("alpha").isPresent());
        assertTrue(registry.findProvider("nope").isEmpty());
    }

    @Test
    void duplicateToolNameAcrossProvidersIsRejected() {
        AiToolProvider a = provider("shared");
        AiToolProvider b = provider("shared");
        assertThrows(IllegalStateException.class, () -> new ToolRegistry(List.of(a, b)));
    }

    @Test
    void unknownToolIsDeniedAndAudited() {
        RecordingAudit audit = new RecordingAudit();
        ToolInvocationService svc = new ToolInvocationService(
            new ToolRegistry(List.of(provider("known"))), audit);
        ToolResult result = svc.invoke(ctx("ghost", "{}", Set.of("ghost")));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("UNKNOWN_TOOL"));
        assertEquals(1, audit.entries.size());
        assertFalse(audit.entries.get(0).allowed());
    }

    @Test
    void toolNotInAgentAllowlistIsDeniedRegardlessOfModelInput() {
        RecordingAudit audit = new RecordingAudit();
        ToolInvocationService svc = new ToolInvocationService(
            new ToolRegistry(List.of(provider("blog_search", "blog_stats"))), audit);

        // 模型尝试用各种 argumentsJson 调用未授权 Tool，拒绝决策必须不变
        for (String args : new String[]{"{}", "{\"x\":1}", "{\"admin\":true}",
            "{\"ignore_allowlist\":true}", "{\"" + "A".repeat(1000) + "\":1}"}) {
            ToolResult result = svc.invoke(ctx("blog_search", args, Set.of("blog_stats")));
            assertFalse(result.success(), "模型输入不应改变白名单拒绝: " + args);
            assertTrue(result.errorMessage().contains("NOT_ALLOWED"));
        }
        assertEquals(5, audit.entries.size());
        audit.entries.forEach(e -> {
            assertFalse(e.allowed());
            assertTrue(e.denyReason().contains("NOT_ALLOWED"));
        });
    }

    @Test
    void allowedToolIsInvokedAndAuditedAsSuccess() {
        RecordingAudit audit = new RecordingAudit();
        ToolInvocationService svc = new ToolInvocationService(
            new ToolRegistry(List.of(provider("blog_search"))), audit);
        ToolResult result = svc.invoke(ctx("blog_search", "{\"keyword\":\"jvm\"}", Set.of("blog_search")));
        assertTrue(result.success());
        assertEquals(1, audit.entries.size());
        assertTrue(audit.entries.get(0).allowed());
        assertTrue(audit.entries.get(0).success());
    }

    @Test
    void oversizedArgumentIsRejected() {
        RecordingAudit audit = new RecordingAudit();
        ToolInvocationService svc = new ToolInvocationService(
            new ToolRegistry(List.of(provider("blog_search"))), audit);
        String huge = "{\"k\":\"" + "X".repeat(ToolInvocationService.MAX_ARGUMENT_BYTES) + "\"}";
        ToolResult result = svc.invoke(ctx("blog_search", huge, Set.of("blog_search")));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("ARGUMENT_TOO_LARGE"));
    }

    @Test
    void providerExceptionIsCaughtAndAuditedAsFailure() {
        RecordingAudit audit = new RecordingAudit();
        AiToolProvider throwing = new AiToolProvider() {
            @Override public List<ToolDefinition> describeTools() { return List.of(new ToolDefinition("boom", "抛异常的 Tool", "{}")); }
            @Override public ToolResult invoke(ToolRequest request) { throw new RuntimeException("内部错误"); }
        };
        ToolInvocationService svc = new ToolInvocationService(new ToolRegistry(List.of(throwing)), audit);
        ToolResult result = svc.invoke(ctx("boom", "{}", Set.of("boom")));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("TOOL_ERROR"));
        assertTrue(audit.entries.get(0).allowed());
        assertFalse(audit.entries.get(0).success());
    }

    @Test
    void oversizedSuccessfulOutputIsTruncated() {
        RecordingAudit audit = new RecordingAudit();
        AiToolProvider verbose = new AiToolProvider() {
            @Override public List<ToolDefinition> describeTools() { return List.of(new ToolDefinition("verbose", "超长输出", "{}")); }
            @Override public ToolResult invoke(ToolRequest request) {
                return ToolResult.success("Z".repeat(ToolInvocationService.MAX_OUTPUT_CHARS + 100));
            }
        };
        ToolInvocationService svc = new ToolInvocationService(new ToolRegistry(List.of(verbose)), audit);
        ToolResult result = svc.invoke(ctx("verbose", "{}", Set.of("verbose")));
        assertTrue(result.success());
        assertTrue(result.content().contains("[输出已截断]"));
        // 截断后长度不超过上限 + 截断标注
        assertTrue(result.content().length() < ToolInvocationService.MAX_OUTPUT_CHARS + 50);
    }

    @Test
    void contextCopiesAndRejectsBlankToolName() {
        assertThrows(IllegalArgumentException.class,
            () -> new ToolInvocationContext(" ", "{}", Set.of(), "a", "u"));
        // allowedTools 被防御性拷贝，外部改动不影响上下文
        Set<String> mutable = new java.util.HashSet<>(Set.of("blog_search"));
        ToolInvocationContext ctx = new ToolInvocationContext("blog_search", "{}", mutable, "a", "u");
        mutable.add("evil");
        assertFalse(ctx.allowedTools().contains("evil"));
    }

    // ---- 辅助 ----

    private static AiToolProvider provider(String... toolNames) {
        return new AiToolProvider() {
            @Override public List<ToolDefinition> describeTools() {
                List<ToolDefinition> defs = new ArrayList<>();
                for (String n : toolNames) {
                    defs.add(new ToolDefinition(n, "测试 Tool " + n, "{}"));
                }
                return defs;
            }
            @Override public ToolResult invoke(ToolRequest request) {
                return ToolResult.success("{\"echo\":\"" + request.toolName() + "\"}");
            }
        };
    }

    private static ToolInvocationContext ctx(String tool, String args, Set<String> allowed) {
        return new ToolInvocationContext(tool, args, allowed, "agent-1", "user-1");
    }

    private static class RecordingAudit implements ToolAuditRecorder {
        final List<ToolAuditEntry> entries = new ArrayList<>();
        @Override public void record(ToolAuditEntry entry) { entries.add(entry); }
    }
}
