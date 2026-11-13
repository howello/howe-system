package com.howe.common.tool.model;

import java.util.List;
import java.util.Map;

/**
 * 工具执行的通用结果。
 *
 * <p>只承载普通数据，不引用 MCP、HTTP 或 {@code AjaxResult} 类型。
 * {@code warnings} 是可扩展的普通 JSON 对象列表，元素至少含 {@code code}；
 * {@code summary} 只用于对外文本摘要，不写入结构化正文。</p>
 *
 * @param data 业务数据；列表工具为数组，详情工具为对象
 * @param page 分页信息；非分页工具为 null
 * @param hasMore 是否还有下一页；非分页工具为 false
 * @param warnings 警告列表；无警告时为空列表，成功时不为 null
 * @param error 错误对象；成功时为 null
 * @param summary 简短摘要
 */
public record ToolResult(Object data, ToolPage page, boolean hasMore, List<Map<String, Object>> warnings,
        ToolError error, String summary) {

    /** 防御性复制警告列表。 */
    public ToolResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
