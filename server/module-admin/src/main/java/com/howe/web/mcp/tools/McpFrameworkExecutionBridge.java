package com.howe.web.mcp.tools;

import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.utils.SecurityUtils;
import com.howe.framework.security.context.PermissionContextHolder;
import com.howe.mcp.service.spi.McpToolExecutionBridge;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 在 MCP 工具线程中绑定业务权限上下文。
 *
 * <p>数据权限切面读取 {@link PermissionContextHolder}；MCP worker 没有 HTTP 请求属性，
 * 因此这里把工具声明的权限串绑定到当前线程，工具结束后恢复。绑定值只来自代码定义，
 * 不接受模型参数或客户端输入。</p>
 *
 * <p>{@code anyOf} 只绑定当前账号实际拥有的权限：数据权限切面按 {@code containsAny}
 * 判断，若把未授予的候选权限一并写入，会放大该账号可见的数据范围。</p>
 */
public final class McpFrameworkExecutionBridge implements McpToolExecutionBridge {

    /**
     * 在权限作用域内执行工具。
     *
     * @param access 当前工具的通用访问要求
     * @param action 工具操作
     * @return 操作结果
     * @param <T> 结果类型
     */
    @Override
    public <T> T execute(ToolAccess access, Supplier<T> action) {
        Objects.requireNonNull(access);
        Objects.requireNonNull(action);
        String permission = permissionOf(access, grantedPermissions());
        if (permission == null) {
            return action.get();
        }
        try (AutoCloseable ignored = PermissionContextHolder.bind(permission)) {
            return action.get();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("恢复 MCP 权限上下文失败", exception);
        }
    }

    /**
     * 读取当前账号的权限集合；无法读取时返回空集合，后续按不放行处理。
     *
     * @return 当前权限集合
     */
    private static Set<String> grantedPermissions() {
        try {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null || loginUser.getPermissions() == null) {
                return Set.of();
            }
            return loginUser.getPermissions();
        } catch (RuntimeException exception) {
            return Set.of();
        }
    }

    /**
     * 汇总需要参与数据权限判定的权限串。
     *
     * <p>{@code allOf} 全部绑定（工具已要求全部具备）；{@code anyOf} 只绑定当前账号
     * 实际拥有的项，避免把未授予的候选权限注入数据权限切面。平台通配权限
     * {@code *:*:*} 视为满足全部声明，仍只绑定声明范围内且实际可用的权限串。</p>
     *
     * @param access 工具访问要求
     * @param granted 当前账号权限
     * @return 逗号分隔权限串；没有可绑定权限时返回 null
     */
    private static String permissionOf(ToolAccess access, Set<String> granted) {
        var merged = new LinkedHashSet<String>();
        merged.addAll(access.allOf());
        boolean wildcard = granted.contains("*:*:*");
        for (String candidate : access.anyOf()) {
            if (wildcard || granted.contains(candidate)) {
                merged.add(candidate);
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        return String.join(",", merged);
    }
}
