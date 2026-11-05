package com.howe.common.utils.http;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.common.exception.ServiceException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * 远程文件地址安全校验
 *
 * <p>供 APP 资源包、分享远程链接共用。只做「地址本身」的检查：
 * 协议必须 https、不带用户名密码、端口限 443、解析结果必须全部是公网地址。</p>
 *
 * <p>SSRF 防线是「每一跳都重新校验 + 禁止自动跟随重定向」：先解析再请求并不能
 * 保证连接用的还是已校验的 IP（DNS 可能变化），所以不能只在这里校验一次就完事。</p>
 *
 * @author howe
 */
public final class RemoteFileGuard {

    private RemoteFileGuard() {
    }

    /**
     * 校验远程地址
     *
     * @param url 地址
     * @return 解析后的 URI
     * @throws ServiceException 不满足安全要求时抛出，信息直接面向用户
     */
    public static URI validate(String url) {
        if (StrUtil.isBlank(url)) {
            throw new ServiceException("远程地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new ServiceException("远程地址格式不合法");
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            throw new ServiceException("远程地址必须使用 https 协议");
        }
        if (uri.getUserInfo() != null) {
            throw new ServiceException("远程地址不允许携带用户名密码");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new ServiceException("远程地址只允许使用 443 端口");
        }
        String host = uri.getHost();
        if (StrUtil.isBlank(host)) {
            throw new ServiceException("远程地址缺少主机名");
        }
        requirePublicHost(host);
        return uri;
    }

    /**
     * 校验主机解析结果全部为公网地址
     *
     * <p>拦截回环、私网、链路本地、组播与保留地址。任一解析结果命中内网即拒绝——
     * 不能因为「其中一个地址是公网」就放行，请求可能落到内网那个上。</p>
     *
     * @param host 主机名
     * @throws ServiceException 含内网地址时抛出
     */
    public static void requirePublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ServiceException("无法解析主机名：" + host);
        }
        for (InetAddress address : addresses) {
            if (isPrivateAddress(address)) {
                throw new ServiceException("远程地址解析到了内网或保留地址，已拒绝访问");
            }
        }
    }

    /**
     * 判断是否内网 / 保留地址
     *
     * <p>hutool 的 {@code isInnerIP} 只认 IPv4 私网段，IPv6 的回环、链路本地、
     * 唯一本地与组播要自己判。</p>
     *
     * @param address 地址
     * @return 内网或保留地址返回 true
     */
    private static boolean isPrivateAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        String hostAddress = address.getHostAddress();
        return NetUtil.isInnerIP(hostAddress);
    }
}
