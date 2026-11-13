package com.howe.mcp.service.spi;

import com.howe.common.tool.model.ToolAccess;
import java.util.function.Supplier;

/**
 * 业务执行上下文桥接。
 *
 * <p>MCP 工具线程没有 HTTP 请求属性，而数据权限切面依赖请求级权限上下文。
 * 由 {@code module-admin} 提供实现，在工具执行期间按通用访问要求绑定当前工具的业务权限，
 * 结束后恢复原值；{@code module-mcp} 不引入 framework 依赖。</p>
 *
 * <p>入参使用 common 的 {@link ToolAccess}，桥接实现不需要引用 MCP 工具定义或 SDK 类型。</p>
 */
public interface McpToolExecutionBridge {

    /**
     * 在可清理的业务权限上下文中执行工具。
     *
     * @param access 当前工具的通用访问要求
     * @param action 工具操作
     * @return 操作结果
     * @param <T> 结果类型
     */
    <T> T execute(ToolAccess access, Supplier<T> action);
}
