package com.howe.common.tool.support;

import com.howe.common.tool.model.ToolError;
import com.howe.common.tool.model.ToolPage;
import com.howe.common.tool.model.ToolResult;
import java.util.List;
import java.util.Map;

/**
 * 通用工具结果构造器。
 *
 * <p>成功结果统一为 {@code data/page/hasMore/warnings/error}；摘要只用于对外文本，
 * 结构化正文放在 {@code data}。本类型不引用 MCP 协议结果类型。</p>
 */
public final class ToolResults {

    /** 摘要长度上限，避免文本与结构化内容重复占用结果预算。 */
    private static final int SUMMARY_LIMIT = 120;

    /** 工具结果构造器不允许实例化。 */
    private ToolResults() {
    }

    /**
     * 构造分页成功结果。
     *
     * @param data 当前页数据
     * @param page 分页信息
     * @param hasMore 是否还有下一页
     * @param summary 简短摘要
     * @return 通用成功结果
     */
    public static ToolResult page(List<?> data, ToolPage page, boolean hasMore, String summary) {
        return success(data, page, hasMore, List.of(), summary);
    }

    /**
     * 构造非分页成功结果。
     *
     * @param data 返回数据
     * @param summary 简短摘要
     * @return 通用成功结果
     */
    public static ToolResult detail(Object data, String summary) {
        return success(data, null, false, List.of(), summary);
    }

    /**
     * 构造带警告的成功结果。
     *
     * @param data 返回数据
     * @param page 分页信息，可为 null
     * @param hasMore 是否还有下一页
     * @param warnings 警告列表，可为 null
     * @param summary 简短摘要
     * @return 通用成功结果
     */
    public static ToolResult success(Object data, ToolPage page, boolean hasMore,
            List<Map<String, Object>> warnings, String summary) {
        return new ToolResult(data, page, hasMore, warnings == null ? List.of() : List.copyOf(warnings), null,
                normalize(summary, "工具调用完成"));
    }

    /**
     * 构造稳定错误结果。
     *
     * @param code 错误码
     * @param message 固定提示
     * @param retryable 是否允许重试
     * @return 通用错误结果
     */
    public static ToolResult failure(String code, String message, boolean retryable) {
        return new ToolResult(null, null, false, List.of(), new ToolError(code, message, retryable),
                normalize(message, "工具调用未完成"));
    }

    /**
     * 截断摘要，避免文本占用过多结果预算。
     *
     * <p>先去除首尾空白；为空时使用兜底值；超过 120 个字符时截断并追加省略号。</p>
     *
     * @param value 原始摘要
     * @param fallback 空值时的兜底摘要
     * @return 规范化后的摘要
     */
    public static String normalize(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "…";
    }
}
