package com.howe.mcp.service.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 地址、客户端回调和元数据出站地址的统一校验。
 */
@Slf4j
public final class McpUriPolicy {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    /** 工具类不允许实例化。 */
    private McpUriPolicy() {
    }

    /**
     * 校验公共站点基址；本地 HTTP 只在显式开发配置下允许。
     *
     * @param value 配置值
     * @param allowLocalHttp 是否允许本机开发地址
     * @return 不带末尾斜杠的站点根地址
     */
    public static URI requireBaseUrl(String value, boolean allowLocalHttp) {
        URI uri = requireAbsolute(value);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        boolean local = allowLocalHttp && "http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri);
        if ((!secure && !local) || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                || uri.getRawQuery() != null) {
            throw new IllegalArgumentException("MCP 公共基址必须是 HTTPS 站点根地址");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String normalizedHost = host(uri);
        String authority = normalizedHost.contains(":") ? "[" + normalizedHost + "]" : normalizedHost;
        int port = uri.getPort();
        if (port != -1 && !("https".equals(scheme) && port == 443) && !("http".equals(scheme) && port == 80)) {
            authority += ":" + port;
        }
        return URI.create(scheme + "://" + authority);
    }

    /**
     * 校验 OAuth 回调地址，仅对明确的 loopback 地址开放 HTTP。
     *
     * @param value 回调地址
     * @return 已校验的地址
     */
    public static URI requireRedirectUri(String value) {
        URI uri = requireAbsolute(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri))) {
            throw new IllegalArgumentException("OAuth 回调必须使用 HTTPS 或本机 loopback 地址");
        }
        return uri;
    }

    /**
     * 校验实际回调与登记地址匹配，仅忽略 native HTTP loopback 的端口。
     *
     * @param registered 登记地址
     * @param requested 本次授权地址
     * @return 是否允许该回调
     */
    public static boolean redirectMatches(String registered, String requested) {
        try {
            URI expected = requireRedirectUri(registered);
            URI actual = requireRedirectUri(requested);
            if ("http".equalsIgnoreCase(expected.getScheme()) && isLoopback(expected)
                    && "http".equalsIgnoreCase(actual.getScheme()) && isLoopback(actual)) {
                return host(expected).equals(host(actual))
                        && expected.getRawPath().equals(actual.getRawPath())
                        && java.util.Objects.equals(expected.getRawQuery(), actual.getRawQuery());
            }
            return registered.equals(requested);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 校验 CIMD 文档 URL 的语法，DNS 与实际连接地址还必须单独校验。
     *
     * @param value 文档 URL
     * @return 已校验的 HTTPS 地址
     */
    public static URI requireMetadataUri(String value) {
        URI uri = requireAbsolute(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || isLoopback(uri)
                || !value.equals(uri.toASCIIString()) || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath())) {
            throw new IllegalArgumentException("客户端元数据必须使用公开的 HTTPS 地址");
        }
        for (String segment : uri.getRawPath().split("/", -1)) {
            String decoded = java.net.URLDecoder.decode(segment.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
            if (".".equals(decoded) || "..".equals(decoded) || decoded.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("客户端元数据路径格式无效");
            }
        }
        return uri;
    }

    /**
     * 判断解析出的地址是否适合元数据出站请求，禁止本地和特殊用途网段。
     *
     * @param address DNS 解析出的地址
     * @return 是否允许连接该地址
     */
    public static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        if (bytes.length == 16) {
            boolean documentation = first == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8;
            boolean tunnel = first == 0x20 && (bytes[1] & 0xff) == 0x02;
            boolean protocol = first == 0x20 && (bytes[1] & 0xff) == 0x01 && (bytes[2] & 0xfe) == 0;
            boolean newDocumentation = first == 0x3f && (bytes[1] & 0xff) == 0xff && (bytes[2] & 0xf0) == 0;
            boolean as112 = first == 0x26 && (bytes[1] & 0xff) == 0x20 && (bytes[2] & 0xff) == 0
                    && (bytes[3] & 0xff) == 0x4f && (bytes[4] & 0xff) == 0x80 && (bytes[5] & 0xff) == 0;
            return (first & 0xe0) == 0x20 && !documentation && !tunnel && !protocol && !newDocumentation && !as112;
        }
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        return first != 0 && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 192 && second == 0 && (third == 0 || third == 2))
                && !(first == 192 && (second == 31 && third == 196 || second == 52 && third == 193
                        || second == 88 && third == 99 || second == 175 && third == 48))
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113);
    }

    /** 判断是否为明确的 native loopback 主机，不执行 DNS 查询。 */
    private static boolean isLoopback(URI uri) {
        return LOOPBACK_HOSTS.contains(host(uri));
    }

    /** 统一 IPv6 方括号和 DNS 大小写，保留不同主机名之间的区别。 */
    private static String host(URI uri) {
        String value = uri.getHost().toLowerCase(Locale.ROOT);
        return value.startsWith("[") && value.endsWith("]") ? value.substring(1, value.length() - 1) : value;
    }

    /** 拒绝非绝对地址、凭据、fragment、非法端口与无效主机。 */
    private static URI requireAbsolute(String value) {
        if (value == null || value.isBlank() || value.length() > 2048 || !value.equals(value.trim())) {
            throw new IllegalArgumentException("OAuth 地址格式无效");
        }
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("OAuth 地址格式无效");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("OAuth 地址格式无效", e);
        }
    }
}
