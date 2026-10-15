package com.howe.share.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import com.howe.share.constant.ShareConstants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 分享会话票据
 *
 * <p>两类票据都是 HttpOnly Cookie（浏览器加载 img/iframe/video 时无法带自定义头，只能靠同源 cookie）：</p>
 * <ul>
 *   <li><b>密码票据</b>：密码校验通过后下发，证明「已验证密码」，用于放行 {@code open}；</li>
 *   <li><b>就绪票据</b>：进入就绪态后下发，证明「已计数」，用于放行 {@code file} / {@code download}。
 *   浏览器一次预览会发多个请求，靠它避免被提前焚毁。</li>
 * </ul>
 *
 * @author howe
 */
@Component
@RequiredArgsConstructor
public class ShareTicketService {

    /** 票据默认有效期（秒） */
    private static final int DEFAULT_TTL = 300;

    private final RedisCache redisCache;

    /**
     * 下发密码票据
     *
     * @param code     短码
     * @param request  请求（判断是否 https，决定 cookie 的 Secure）
     * @param response 响应
     */
    public void issuePwd(String code, HttpServletRequest request, HttpServletResponse response) {
        issue(ShareConstants.TICKET_KEY_PWD, ShareConstants.TICKET_COOKIE_PWD, code, request, response);
    }

    /**
     * 下发就绪票据
     *
     * @param code     短码
     * @param request  请求
     * @param response 响应
     */
    public void issueView(String code, HttpServletRequest request, HttpServletResponse response) {
        issue(ShareConstants.TICKET_KEY_VIEW, ShareConstants.TICKET_COOKIE_VIEW, code, request, response);
    }

    /**
     * 是否持有该短码的密码票据
     *
     * @param code    短码
     * @param request 请求
     * @return 有效返回 true
     */
    public boolean hasPwd(String code, HttpServletRequest request) {
        return validate(ShareConstants.TICKET_KEY_PWD, ShareConstants.TICKET_COOKIE_PWD, code, request);
    }

    /**
     * 是否持有该短码的就绪票据
     *
     * @param code    短码
     * @param request 请求
     * @return 有效返回 true
     */
    public boolean hasView(String code, HttpServletRequest request) {
        return validate(ShareConstants.TICKET_KEY_VIEW, ShareConstants.TICKET_COOKIE_VIEW, code, request);
    }

    /**
     * 签发票据：随机 token 存 Redis（值为短码），并写 HttpOnly Cookie
     */
    private void issue(String keyPrefix, String cookieName, String code,
                       HttpServletRequest request, HttpServletResponse response) {
        int ttl = ttl();
        String token = IdUtil.fastSimpleUUID();
        redisCache.setCacheObject(keyPrefix + token, code, ttl, TimeUnit.SECONDS);
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(request != null && request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(ttl))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 校验票据：存在且值等于短码
     */
    private boolean validate(String keyPrefix, String cookieName, String code, HttpServletRequest request) {
        String token = readCookie(request, cookieName);
        if (StrUtil.isBlank(token)) {
            return false;
        }
        Object stored = redisCache.getCacheObject(keyPrefix + token);
        return stored != null && code.equals(String.valueOf(stored));
    }

    /**
     * 读取指定名称的 cookie
     */
    private String readCookie(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 票据有效期（秒）
     */
    private int ttl() {
        return ConfigUtils.getInt(ConfigConstants.SHARE_TICKET_TTL, DEFAULT_TTL);
    }
}
