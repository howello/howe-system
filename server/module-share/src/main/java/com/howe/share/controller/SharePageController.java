package com.howe.share.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.exception.ServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 短链页面 控制层
 *
 * <p>两件事：</p>
 * <ol>
 *   <li>{@code GET /s/{code}}：读取自包含的公开分享页模板，把短码注入后以 HTML 返回。
 *   服务端 forward 不会改变浏览器地址栏，靠 query 参数传值前端 JS 读不到，所以这里直接渲染注入。</li>
 *   <li>{@code GET /share-page/**}：把分享页的静态资源（vendor 下的 marked / DOMPurify）以匿名方式发出。
 *   走 {@code @Anonymous} 而不是往 {@code SecurityConfig} 的静态放行清单里加规则——
 *   项目约定匿名放行一律用 {@code @Anonymous}，且 {@code SecurityConfig} 里既有的
 *   {@code /**}{@code .html}、{@code /**}{@code .js} 这类 Ant 写法只匹配单段路径，覆盖不到 {@code share-page/xxx} 这类多段路径。</li>
 * </ol>
 *
 * @author howe
 */
@Tag(name = "分享-短链页面", description = "短链入口渲染")
@Anonymous
@RestController
public class SharePageController {

    /** 页面模板在 classpath 下的位置 */
    private static final String PAGE_LOCATION = "static/share-page/index.html";

    /** 短码路径前缀 */
    private static final String CODE_PATH_PREFIX = "/s/";

    /**
     * 页面模板里的短码占位符
     *
     * <p>刻意避开 JS 标识符 {@code window.__SHARE_CODE__}：若占位符是该标识符的子串，
     * 全局替换会把属性名一起改掉（短码以数字开头时 {@code window.5abc} 是非法语法，
     * 会导致整段脚本解析失败）。此处只替换字符串字面量里的占位符。</p>
     */
    private static final String CODE_PLACEHOLDER = "@@SHARE_CODE@@";

    /** 页面模板缓存（首次读取后复用） */
    private volatile String pageTemplate;

    /**
     * 短链入口：渲染分享页并注入短码
     *
     * <p>
     * <b>匹配所有 {@code /s/**}</b>，而不只是 {@code /s/{code}}。短链域名（如
     * {@code s.wyantao.com}）只对外提供分享访问，不该出现「没有任何 handler」的 500
     * 或「认证失败」的 401 —— 那两种响应既暴露了这是后端服务，也会让人以为地址打错了。
     * 现在无论是 {@code /s}、{@code /s/mq4yTU9A} 还是 {@code /s/share/lib/x.ini}，
     * 一律返回分享页；页面拿到的短码无效时由前端渲染「链接不存在」，与真实失效链接
     * 的用户体验完全一致，也无法据此判断短码是否存在。
     * </p>
     *
     * @param request 请求（用于取原始路径）
     * @return HTML 响应
     */
    @Operation(summary = "短链入口", description = "匹配 /s/** 全部路径；路径中取不到有效短码时由页面显示「链接不存在」")
    @GetMapping(value = "/s", produces = "text/html;charset=UTF-8")
    public ResponseEntity<byte[]> pageRoot() {
        return renderPage("");
    }

    /**
     * 短链入口（带子路径）
     *
     * @param request 请求
     * @return HTML 响应
     */
    @Operation(summary = "短链入口（含子路径）", description = "同 /s，路径中的第一段作为短码")
    @GetMapping(value = "/s/**", produces = "text/html;charset=UTF-8")
    public ResponseEntity<byte[]> page(HttpServletRequest request) {
        return renderPage(extractCode(request));
    }

    /**
     * 渲染分享页
     *
     * <p>
     * 空短码也走这里：把空串注入模板，页面请求 {@code /share/public/detail?code=}
     * 会得到「链接不存在」，不需要前端再做判空分支。
     * </p>
     *
     * @param code 短码，可为空
     * @return HTML 响应
     */
    private ResponseEntity<byte[]> renderPage(String code) {
        String html = template().replace(CODE_PLACEHOLDER, sanitizeCode(code));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/html;charset=UTF-8"));
        headers.setCacheControl(CacheControl.noStore());
        headers.add("X-Robots-Tag", "noindex");
        return new ResponseEntity<>(html.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }

    /**
     * 从请求路径里取短码
     *
     * <p>
     * 取 {@code /s/} 之后的第一段，忽略后面的追加路径。这样
     * {@code /s/share/lib/x.ini} 会被当成短码 {@code share} 处理（→ 链接不存在），
     * 而不是当成文件路径去读——短链域名下不存在「按路径取文件」这种用法。
     * </p>
     *
     * @param request 请求
     * @return 短码；取不到时返回空串
     */
    private String extractCode(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (fullPath == null) {
            fullPath = request.getRequestURI();
        }
        if (fullPath == null || !fullPath.startsWith(CODE_PATH_PREFIX)) {
            return "";
        }
        String rest = fullPath.substring(CODE_PATH_PREFIX.length());
        int idx = rest.indexOf('/');
        return idx >= 0 ? rest.substring(0, idx) : rest;
    }

    /**
     * 读取并缓存页面模板
     *
     * @return 模板内容
     */
    private String template() {
        String cached = pageTemplate;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (pageTemplate == null) {
                ClassPathResource resource = new ClassPathResource(PAGE_LOCATION);
                try (InputStream in = resource.getInputStream()) {
                    pageTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new ServiceException("分享页模板读取失败：" + e.getMessage());
                }
            }
            return pageTemplate;
        }
    }

    /**
     * 短码只允许 Base62 字符，避免注入到页面脚本里
     *
     * @param code 原始短码
     * @return 过滤后的短码
     */
    private String sanitizeCode(String code) {
        return code == null ? "" : code.replaceAll("[^0-9A-Za-z]", "");
    }
}
