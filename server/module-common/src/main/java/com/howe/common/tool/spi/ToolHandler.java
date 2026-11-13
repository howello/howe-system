package com.howe.common.tool.spi;

import com.howe.common.tool.model.ToolExecutionContext;
import com.howe.common.tool.model.ToolResult;
import java.util.Map;

/**
 * 工具执行处理器。
 *
 * <p>由所属业务模块实现，接收平台构造的可信上下文与已校验参数，返回通用结果。
 * 实现不得引用 MCP SDK、OAuth 类型或协议会话对象。</p>
 */
@FunctionalInterface
public interface ToolHandler {

    /**
     * 执行工具。
     *
     * @param context 可信执行上下文
     * @param arguments 已通过校验的参数
     * @return 通用工具结果
     * @throws com.howe.common.tool.exception.ToolException 受控业务错误
     */
    ToolResult handle(ToolExecutionContext context, Map<String, Object> arguments);
}
