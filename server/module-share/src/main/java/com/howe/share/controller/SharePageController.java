package com.howe.share.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.exception.ServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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
@Tag(name = "分享-短链页面", description = "短链入口渲染与公开页静态资源")
@Anonymous
@RestController
public class SharePageController {

    /** 页面模板在 classpath 下的位置 */
    private static final String PAGE_LOCATION = "static/share-page/index.html";

    /** 静态资源前缀 */
    private static final String ASSET_PREFIX = "/share-page/";

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
     * @param code 短码
     * @return HTML 响应
     */
    @Operation(summary = "短链入口")
    @GetMapping(value = "/s/{code}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<byte[]> page(@PathVariable("code") String code) {
        String html = template().replace(CODE_PLACEHOLDER, sanitizeCode(code));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/html;charset=UTF-8"));
        headers.setCacheControl(CacheControl.noStore());
        headers.add("X-Robots-Tag", "noindex");
        return new ResponseEntity<>(html.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }

    /**
     * 分享页静态资源（vendor 下的 marked / DOMPurify 等）
     *
     * @param request 请求
     * @return 资源响应
     */
    @Operation(summary = "分享页静态资源")
    @GetMapping(ASSET_PREFIX + "**")
    public ResponseEntity<Resource> asset(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String relative = fullPath == null ? "" : fullPath.substring(ASSET_PREFIX.length());
        if (relative.isEmpty() || relative.contains("..")) {
            throw new ServiceException("分享页资源路径不合法");
        }
        Resource resource = new ClassPathResource("static/share-page/" + relative);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(relative).orElse(MediaType.APPLICATION_OCTET_STREAM);
        CacheControl cacheControl = relative.endsWith(".html")
                ? CacheControl.noStore() : CacheControl.maxAge(Duration.ofHours(1));
        return ResponseEntity.ok().contentType(mediaType).cacheControl(cacheControl).body(resource);
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
