package com.howe.main.handler;

import com.alibaba.fastjson.JSON;
import com.howe.common.enums.exception.MainExceptionEnum;
import com.howe.common.exception.child.MainException;
import com.howe.common.utils.request.AjaxResult;
import com.howe.common.utils.servlet.ServletUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 15:19 星期二
 * <p>@Version 1.0
 * <p>@Description 权限认证失败处理
 */
@Component
public class UnAuthHandler implements AuthenticationEntryPoint, Serializable {
    /**
     * Commences an authentication scheme.
     * <p>
     * <code>ExceptionTranslationFilter</code> will populate the <code>HttpSession</code>
     * attribute named
     * <code>AbstractAuthenticationProcessingFilter.SPRING_SECURITY_SAVED_REQUEST_KEY</code>
     * with the requested target URL before calling this method.
     * <p>
     * Implementations should modify the headers on the <code>ServletResponse</code> as
     * necessary to commence the authentication process.
     *
     * @param request       that resulted in an <code>AuthenticationException</code>
     * @param response      so that the user agent can begin authentication
     * @param authException that caused the invocation
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        AjaxResult<Object> error = AjaxResult.error(MainExceptionEnum.AUTH_FAILED.getCode(),
                MainException.assembleMsg(MainExceptionEnum.AUTH_FAILED, 2, "请求访问：",
                        request.getRequestURI()));
        ServletUtils.renderString(response, JSON.toJSONString(error));
    }
}
