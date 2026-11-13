package com.howe.framework.security.context;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import com.howe.common.core.text.Convert;

/**
 * 权限信息
 *
 * <p>HTTP 请求内沿用请求级属性；MCP 等没有请求属性的执行线程使用显式作用域绑定，
 * 由调用方在 finally 中关闭，避免权限条件跨任务残留。</p>
 *
 * @author howe
 */
public class PermissionContextHolder
{
    private static final String PERMISSION_CONTEXT_ATTRIBUTES = "PERMISSION_CONTEXT";

    /** 显式绑定的权限条件，仅对当前线程和当前作用域有效。 */
    private static final ThreadLocal<String> BOUND_PERMISSION = new ThreadLocal<>();

    public static void setContext(String permission)
    {
        if (hasRequestAttributes())
        {
            RequestContextHolder.currentRequestAttributes().setAttribute(PERMISSION_CONTEXT_ATTRIBUTES, permission,
                    RequestAttributes.SCOPE_REQUEST);
            return;
        }
        BOUND_PERMISSION.set(permission);
    }

    /**
     * 读取当前权限条件。
     *
     * <p>优先返回显式绑定值；其次读取存在的请求属性；都没有时返回 null，
     * 不再因缺少 HTTP 请求而抛错。</p>
     *
     * @return 当前权限条件，可能为 null
     */
    public static String getContext()
    {
        String bound = BOUND_PERMISSION.get();
        if (bound != null)
        {
            return bound;
        }
        if (!hasRequestAttributes())
        {
            return null;
        }
        return Convert.toStr(RequestContextHolder.currentRequestAttributes().getAttribute(PERMISSION_CONTEXT_ATTRIBUTES,
                RequestAttributes.SCOPE_REQUEST));
    }

    /**
     * 显式绑定权限条件，返回可关闭作用域。
     *
     * <p>关闭时恢复绑定前的值；没有原值时移除，避免线程复用时残留。</p>
     *
     * @param permission 本次执行需要读取的权限串，可为 null
     * @return 关闭时恢复原值的作用域
     */
    public static AutoCloseable bind(String permission)
    {
        String previous = BOUND_PERMISSION.get();
        BOUND_PERMISSION.set(permission);
        return () ->
        {
            if (previous == null)
            {
                BOUND_PERMISSION.remove();
            }
            else
            {
                BOUND_PERMISSION.set(previous);
            }
        };
    }

    /** 判断当前线程是否存在可用的 HTTP 请求属性。 */
    private static boolean hasRequestAttributes()
    {
        return RequestContextHolder.getRequestAttributes() != null;
    }
}
