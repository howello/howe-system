package com.howe.share.webdav;

import cn.hutool.core.util.StrUtil;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineImpl;
import com.howe.common.exception.ServiceException;
import com.howe.share.domain.vo.WebDavEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebDAV 客户端封装
 *
 * <p>基于 sardine：hutool 高层 API 的 Method 枚举不含 PROPFIND，故本模块在
 * 「网络/IO 统一 hutool」的红线上开一个显式例外。按凭据指纹复用 {@link Sardine} 实例，
 * 凭据变更自动重建。</p>
 *
 * <p>所有对外路径（{@code path}）都是「相对源根地址」的形式，以 {@code /} 开头；
 * 内部负责拼成完整 URL 并做路径分段编码（中文/空格安全）。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class ShareWebDavClient {

    /** 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 15000;

    /** 读取超时（毫秒） */
    private static final int SOCKET_TIMEOUT_MS = 60000;

    /** 缓存上限：超过后整体清空重建，避免长期运行堆积无用连接池 */
    private static final int CACHE_LIMIT = 64;

    /** 按凭据指纹缓存 Sardine 实例 */
    private final Map<String, Sardine> clientCache = new ConcurrentHashMap<>();

    /**
     * 列出目录内容
     *
     * @param auth 访问凭据
     * @param path 相对源根的路径（以 / 开头），为空按根处理
     * @return 目录条目集合（不含目录自身）
     */
    public List<WebDavEntry> list(WebDavAuth auth, String path) {
        String base = baseUrl(auth);
        String full = buildUrl(base, path);
        try {
            List<DavResource> resources = sardineFor(auth).list(full);
            // PROPFIND Depth:1 的返回列表里包含请求的集合自身，必须剔除：
            // 非空目录下它会多出一行「当前文件夹」，空目录下会让前端把「空」误判为「有内容」。
            // 判定方式：条目的相对路径规范化后与请求目录路径相同，即集合自身
            // （部分服务返回的集合地址带/不带末尾斜杠不一，故统一去末尾斜杠后比较）。
            String selfPath = normalizePath(path);
            List<WebDavEntry> entries = new ArrayList<>();
            for (DavResource resource : resources) {
                String rel = relativize(base, resource.getPath());
                if (rel.isEmpty() || "/".equals(rel)) {
                    continue;
                }
                if (selfPath.equals(normalizePath(rel))) {
                    continue;
                }
                entries.add(toEntry(resource, rel));
            }
            return entries;
        } catch (IOException e) {
            throw new ServiceException("浏览 WebDAV 目录失败：" + e.getMessage());
        }
    }

    /**
     * 读取单个文件（或目录）的元数据
     *
     * @param auth 访问凭据
     * @param path 相对源根的路径
     * @return 元数据；不存在时返回 {@code null}
     */
    public WebDavEntry stat(WebDavAuth auth, String path) {
        String base = baseUrl(auth);
        String full = buildUrl(base, path);
        try {
            List<DavResource> resources = sardineFor(auth).getResources(full);
            if (resources == null || resources.isEmpty()) {
                return null;
            }
            DavResource resource = resources.get(0);
            return toEntry(resource, relativize(base, resource.getPath()));
        } catch (IOException e) {
            throw new ServiceException("读取 WebDAV 文件信息失败：" + e.getMessage());
        }
    }

    /**
     * 打开文件字节流（调用方负责关闭）
     *
     * @param auth 访问凭据
     * @param path 相对源根的路径
     * @return 字节流
     */
    public InputStream open(WebDavAuth auth, String path) {
        String full = buildUrl(baseUrl(auth), path);
        try {
            return sardineFor(auth).get(full);
        } catch (IOException e) {
            throw new ServiceException("下载 WebDAV 文件失败：" + e.getMessage());
        }
    }

    /**
     * 连通性测试
     *
     * <p>用与「浏览目录」完全一致的 PROPFIND 调用（{@link #list}）判断连通性，并对根地址做
     * 规范化（补末尾 {@code /}）。不用 sardine 的 {@code exists()}：它发的是 Depth:0 的 PROPFIND，
     * 部分服务（如坚果云）对未以 {@code /} 结尾的集合地址会直接返回 403，导致「能浏览却测不通」。</p>
     *
     * @param auth 访问凭据
     * @return 根目录可列举返回 true
     */
    public boolean test(WebDavAuth auth) {
        String root = buildUrl(baseUrl(auth), null);
        try {
            sardineFor(auth).list(root);
            return true;
        } catch (IOException e) {
            throw new ServiceException("连接 WebDAV 失败：" + e.getMessage()
                    + "；请检查根地址（建议以 / 结尾）、账号与应用密码是否正确");
        }
    }

    /**
     * 取源根地址，缺失时抛业务异常
     */
    private String baseUrl(WebDavAuth auth) {
        if (auth == null || StrUtil.isBlank(auth.baseUrl())) {
            throw new ServiceException("WebDAV 根地址未配置");
        }
        return auth.baseUrl().trim();
    }

    /**
     * 按凭据指纹取（或重建）Sardine 实例
     */
    private Sardine sardineFor(WebDavAuth auth) {
        String username = auth.username() == null ? "" : auth.username();
        String password = auth.password() == null ? "" : auth.password();
        String fingerprint = baseUrl(auth) + "|" + username + "|" + password;
        Sardine cached = clientCache.get(fingerprint);
        if (cached != null) {
            return cached;
        }
        Sardine created = buildSardine(username, password);
        if (clientCache.size() >= CACHE_LIMIT) {
            clientCache.clear();
        }
        clientCache.put(fingerprint, created);
        return created;
    }

    /**
     * 构建带超时的 Sardine 实例
     */
    private Sardine buildSardine(String username, String password) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();
        HttpClientBuilder builder = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig);
        return new SardineImpl(builder, username, password);
    }

    /**
     * 拼完整 URL：base 保证以 / 结尾，path 的分段做 URL 编码
     */
    private String buildUrl(String base, String path) {
        String normalizedBase = base.endsWith("/") ? base : base + "/";
        String trimmed = path == null ? "" : path.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return normalizedBase;
        }
        String encoded = encodeSegments(trimmed);
        // 集合地址必须保留末尾 /：部分服务（如坚果云）对未带末尾斜杠的目录地址返回 403，
        // 而目录条目从 PROPFIND 返回的路径天然带末尾 /，编码时会被 split 丢掉，这里补回来。
        if (trimmed.endsWith("/")) {
            encoded = encoded + "/";
        }
        return normalizedBase + encoded;
    }

    /**
     * 按 / 分段做 URL 编码
     */
    private String encodeSegments(String path) {
        StringBuilder sb = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    /**
     * 把资源绝对路径裁剪成相对源根的路径（以 / 开头）
     */
    private String relativize(String base, String resourcePath) {
        if (StrUtil.isBlank(resourcePath)) {
            return "/";
        }
        String basePath;
        try {
            basePath = URI.create(base).getPath();
        } catch (Exception e) {
            basePath = "/";
        }
        if (StrUtil.isBlank(basePath)) {
            basePath = "/";
        }
        String rel = resourcePath;
        if (rel.startsWith(basePath)) {
            rel = rel.substring(basePath.length());
        }
        if (rel.isEmpty()) {
            return "/";
        }
        return rel.startsWith("/") ? rel : "/" + rel;
    }

    /**
     * 规范化相对路径：统一以 {@code /} 开头、去掉末尾多余的 {@code /}
     *
     * <p>用于判断条目是否为「请求目录自身」：WebDAV 服务对集合地址是否带末尾斜杠
     * 并不统一（坚果云要求带，其它服务可能不带），两侧都规范化后才能可靠比较。</p>
     *
     * @param path 待规范化的路径，可为 {@code null}
     * @return 规范化后的路径，根目录为 {@code /}
     */
    private String normalizePath(String path) {
        if (StrUtil.isBlank(path)) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * DavResource → WebDavEntry
     */
    private WebDavEntry toEntry(DavResource resource, String relPath) {
        String name = resource.getName();
        if (StrUtil.isBlank(name)) {
            int idx = relPath.lastIndexOf('/');
            name = idx >= 0 ? relPath.substring(idx + 1) : relPath;
        }
        // 目录路径统一以 / 结尾：前端「进入子目录」会把该 path 原样回传，服务端据此拼 URL；
        // 保留末尾斜杠可避免部分服务（如坚果云）对无斜杠的集合地址返回 403。
        boolean directory = resource.isDirectory();
        String entryPath = relPath;
        if (directory && !entryPath.endsWith("/")) {
            entryPath = entryPath + "/";
        }
        Long length = resource.getContentLength();
        Date modified = resource.getModified();
        return new WebDavEntry(
                name,
                entryPath,
                directory,
                length == null ? 0L : length,
                resource.getContentType(),
                modified == null ? null : modified.toInstant().toString());
    }
}
