package com.howe.mcp.tool;

import com.howe.common.tool.model.ToolPage;
import com.howe.common.tool.model.ToolResult;
import com.howe.common.tool.support.ToolResults;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用工具结果到 MCP 协议结果的适配器。
 *
 * <p>结构化正文放入 {@code structuredContent}，text content 只放简短摘要。
 * 结果结构保持既有契约：{@code data/page/hasMore/warnings/error}，
 * 不注入 {@code requestId}，失败时 {@code isError} 与非空 error 一致。</p>
 */
public final class McpToolResultAdapter {

    /** 适配器不允许实例化。 */
    private McpToolResultAdapter() {
    }

    /**
     * 把通用结果转换为协议结果。
     *
     * @param result 通用工具结果
     * @return SDK 工具结果
     */
    public static McpSchema.CallToolResult toProtocol(ToolResult result) {
        if (result == null) {
            return failure("invalid_result", "工具结果为空", false);
        }
        if (result.error() != null) {
            return failure(result.error().code(), result.error().message(), result.error().retryable());
        }
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("data", result.data());
        structured.put("page", page(result.page()));
        structured.put("hasMore", result.hasMore());
        structured.put("warnings", result.warnings());
        structured.put("error", null);
        return McpSchema.CallToolResult.builder().isError(false)
                .content(List.of(new McpSchema.TextContent(ToolResults.normalize(result.summary(), "工具调用完成"))))
                .structuredContent(structured).build();
    }

    /**
     * 构造稳定错误结果。
     *
     * @param code 错误码
     * @param message 固定提示
     * @param retryable 是否允许重试
     * @return SDK 工具错误结果
     */
    public static McpSchema.CallToolResult failure(String code, String message, boolean retryable) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("data", null);
        structured.put("page", null);
        structured.put("hasMore", false);
        structured.put("warnings", List.of());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        structured.put("error", error);
        return McpSchema.CallToolResult.builder().isError(true)
                .content(List.of(new McpSchema.TextContent(ToolResults.normalize(message, "工具调用未完成"))))
                .structuredContent(structured).build();
    }

    /**
     * 把分页信息转换为协议字段结构。
     *
     * @param page 分页信息，可为 null
     * @return 协议分页 Map；无分页时返回 null
     */
    private static Map<String, Object> page(ToolPage page) {
        if (page == null) {
            return null;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("number", page.number());
        value.put("size", page.size());
        value.put("returned", page.returned());
        value.put("total", page.total());
        return value;
    }
}
