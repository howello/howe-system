package com.howe.blog.util;

import cn.hutool.core.util.StrUtil;
import com.howe.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * URL 安全校验
 *
 * <p>博客模块存两类不可信 URL：管理员填的站点/图标/订阅地址，以及从第三方 RSS
 * 里解析出来的条目链接。两者最终都会被拼进页面的 {@code href}/{@code src}，
 * 或者被服务端主动请求，所以入库前必须先过这里。</p>
 *
 * <p>两层强度，用途不同：</p>
 * <ul>
 *   <li>{@link #isDisplayableUrl(String)} —— 只做协议白名单，挡 {@code javascript:}、
 *       {@code data:} 这类伪协议。用于所有会进 href/src 的地址。<b>仅靠 HTML 转义挡不住它们</b>：
 *       转义能防属性闭合突破，但 {@code href="javascript:..."} 本身就是合法属性值。</li>
 *   <li>{@link #assertFetchable(String)} —— 在协议白名单之外再拒绝内网目标，
 *       用于服务端会主动发起请求的地址（SSRF 防护）。</li>
 * </ul>
 *
 * @author howe
 */
@Slf4j
public final class UrlGuard {

    private UrlGuard() {
    }

    /**
     * 是否是可安全放进 href/src 的 URL
     *
     * <p>只放行 http/https。协议大小写不敏感，且先 trim——{@code " javascript:alert(1)"}
     * 前导空格在浏览器里照样会被当协议解析。</p>
     *
     * @param url 待校验地址
     * @return 结果
     */
    public static boolean isDisplayableUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        String trimmed = url.trim().toLowerCase(Locale.ROOT);
        // 控制字符会被浏览器忽略后再解析协议，含控制字符的一律拒绝
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) < 0x20 || trimmed.charAt(i) == 0x7F) {
                return false;
            }
        }
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    /**
     * 校验用于展示的 URL，不合法就抛异常
     *
     * @param url   待校验地址
     * @param field 字段名，用于错误提示
     */
    public static void assertDisplayable(String url, String field) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        if (!isDisplayableUrl(url)) {
            throw new ServiceException(field + "必须是 http:// 或 https:// 开头的地址");
        }
    }

    /**
     * 校验服务端将主动请求的 URL：协议白名单 + 拒绝内网目标
     *
     * <p>抓取的是不受控的第三方地址，不挡内网的话，一个填成
     * {@code http://127.0.0.1:8080/actuator} 的订阅源就变成了从应用服务器内部
     * 发起的探针，失败信息还会回显到 {@code last_error} 供人读。</p>
     *
     * <p>注意这只是入库时的静态校验，无法覆盖抓取时被 302 到内网的情况——
     * 那一层由 {@code RssFetcher} 关闭自动重定向、逐跳校验来兜。</p>
     *
     * @param url 待校验地址
     */
    public static void assertFetchable(String url) {
        if (!isDisplayableUrl(url)) {
            throw new ServiceException("订阅地址必须是 http:// 或 https:// 开头的地址");
        }
        String host;
        try {
            host = URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException e) {
            throw new ServiceException("订阅地址格式不合法");
        }
        if (StrUtil.isBlank(host)) {
            throw new ServiceException("订阅地址缺少主机名");
        }
        if (isInternalHost(host)) {
            throw new ServiceException("订阅地址不允许指向内网或本机");
        }
    }

    /**
     * 主机是否解析到内网/本机地址
     *
     * <p>解析失败时按<b>不是内网</b>处理：域名当下解析不了是常态（源站挂了、DNS 抖动），
     * 不该因此拒绝用户填写；真正的兜底在抓取时逐跳校验。</p>
     *
     * @param host 主机名或 IP
     * @return 结果
     */
    public static boolean isInternalHost(String host) {
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isMulticastAddress()) {
                    return true;
                }
                // 云厂商元数据地址：169.254.169.254 已被 isLinkLocalAddress 覆盖，
                // 这里再挡 IPv6 唯一本地地址（fc00::/7），Java 没有现成判断
                byte[] bytes = addr.getAddress();
                if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            log.debug("主机[{}]解析失败，按外网处理：{}", host, e.getMessage());
            return false;
        }
    }
}
