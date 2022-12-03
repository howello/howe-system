package com.howe.main.filter;

import com.howe.common.dto.login.LoginUserDTO;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.token.SecurityUtils;
import com.howe.common.utils.token.TokenUtils;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * token过滤器 验证token有效性
 *
 * @author ruoyi
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    @Autowired
    private TokenUtils tokenUtils;

    private HandlerExceptionResolver resolver;


    @Autowired
    public JwtAuthenticationTokenFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }


    @SneakyThrows
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        try {
            LoginUserDTO loginUser = tokenUtils.getLoginUser(request);
            if (loginUser != null && SecurityUtils.getAuthentication() == null) {
                tokenUtils.verifyToken(loginUser);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null,
                        loginUser.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
            chain.doFilter(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            //解决filter报错全局异常捕获不到
            resolver.resolveException(request, response, null, new CommonException(CommonExceptionEnum.TOKEN_ILLEGAL));
        }
    }
}
