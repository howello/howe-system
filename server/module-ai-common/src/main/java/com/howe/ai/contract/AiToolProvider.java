package com.howe.ai.contract;

import java.util.List;

/**
 * AI Tool 提供方 SPI：把一组只读 Tool 暴露给 AI Agent。
 *
 * <p>一个 Provider 可提供多个 Tool（例如博客 Provider 同时提供搜索、元数据、统计等），
 * 因此 {@link #describeTools()} 返回 Tool 定义清单；{@link #invoke(ToolRequest)} 按
 * {@link ToolRequest#toolName()} 路由到具体实现。提供方只依赖本契约模块，不得反向依赖
 * {@code module-ai} 实现层。</p>
 */
public interface AiToolProvider {
    List<ToolDefinition> describeTools();
    ToolResult invoke(ToolRequest request);
}
