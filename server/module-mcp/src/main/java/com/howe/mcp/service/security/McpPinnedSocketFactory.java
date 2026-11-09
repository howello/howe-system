package com.howe.mcp.service.security;

import cn.hutool.core.io.IoUtil;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Hutool HTTPS 的单次连接安全适配：固定已验证 IP，TLS 仍验证原始域名，不修改全局解析或信任设置。 */
final class McpPinnedSocketFactory extends SSLSocketFactory implements AutoCloseable {
    private final SSLSocketFactory delegate;
    private final String host;
    private final InetAddress address;
    private final int port;
    private final int timeout;
    private final Proxy proxy;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Socket> current = new AtomicReference<>();

    /** 地址的公网检查由获取器在构造前统一完成，本适配器只落实实际连接与 TLS 身份绑定。 */
    McpPinnedSocketFactory(SSLSocketFactory delegate, String host, InetAddress address, int port, int timeout) {
        this(delegate, host, address, port, timeout, Proxy.NO_PROXY);
    }

    /** 系统代理只改变 TCP 出口，隧道目的仍为已经校验的 IP，不让代理重新解析目标域名。 */
    McpPinnedSocketFactory(SSLSocketFactory delegate, String host, InetAddress address, int port, int timeout, Proxy proxy) {
        if (proxy == null || proxy.type() != Proxy.Type.DIRECT && (proxy.type() != Proxy.Type.HTTP
                || !(proxy.address() instanceof InetSocketAddress))) { throw new IllegalArgumentException("不支持的元数据代理"); }
        this.delegate = delegate; this.host = host; this.address = address; this.port = port; this.timeout = timeout; this.proxy = proxy;
    }

    /** JDK URLConnection 先获得无连接套接字，其随后解析出的地址不会决定实际目的地。 */
    @Override
    public Socket createSocket() throws IOException {
        Socket previous = current.getAndSet(null);
        if (previous != null) { previous.close(); }
        return track(new PinnedSocket());
    }

    /** 只接受本工厂创建且实际连接到固定地址的套接字，保留原域名作为 TLS peer host。 */
    @Override
    public Socket createSocket(Socket socket, String hostname, int targetPort, boolean autoClose) throws IOException {
        check(hostname, targetPort);
        if (!autoClose || !(socket instanceof PinnedSocket pinned) || pinned.owner != this || !pinned.targetConnected) {
            throw new IOException("元数据连接目标不符");
        }
        var secure = (SSLSocket) delegate.createSocket(socket, host, port, autoClose);
        var parameters = secure.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        if (!host.contains(":")) { parameters.setServerNames(List.of(new SNIHostName(host))); }
        secure.setSSLParameters(parameters);
        return track(secure);
    }

    /** JDK 的连接重试也只能连接同一个固定地址，不回退到原域名重新选择 IP。 */
    @Override
    public Socket createSocket(String hostname, int targetPort) throws IOException {
        check(hostname, targetPort);
        Socket socket = createSocket();
        try {
            socket.connect(new InetSocketAddress(address, port), timeout);
            socket.setSoTimeout(timeout);
            return createSocket(socket, host, port, true);
        } catch (IOException | RuntimeException exception) { socket.close(); throw exception; }
    }

    /** 不允许调用方指定本地绑定地址影响出站策略。 */
    @Override
    public Socket createSocket(String hostname, int targetPort, InetAddress local, int localPort) throws IOException {
        throw new IOException("不支持元数据自定义本地绑定");
    }

    /** 地址形式重试同样不得改变固定目标。 */
    @Override
    public Socket createSocket(InetAddress target, int targetPort) throws IOException {
        if (!address.equals(target)) { throw new IOException("元数据连接目标不符"); }
        return createSocket(host, targetPort);
    }

    /** 禁止不受控的本地绑定入口。 */
    @Override
    public Socket createSocket(InetAddress target, int targetPort, InetAddress local, int localPort) throws IOException {
        throw new IOException("不支持元数据自定义本地绑定");
    }

    /** 使用平台 TLS 提供者的安全套件，不增加信任全部证书的模式。 */
    @Override
    public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    /** 可用套件继续由标准 TLS 提供者管理。 */
    @Override
    public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

    /** 超时之后迟到的 DNS 或重试不能重新打开网络连接。 */
    private void check(String hostname, int targetPort) throws IOException {
        String actual = hostname.startsWith("[") && hostname.endsWith("]") ? hostname.substring(1, hostname.length() - 1) : hostname;
        if (closed.get() || !host.equalsIgnoreCase(actual) || targetPort != port) { throw new IOException("元数据连接已拒绝"); }
    }

    /** 注册套接字与关闭并发时，迟到的对象立即关闭。 */
    private <T extends Socket> T track(T socket) throws IOException {
        current.set(socket);
        if (closed.get()) { socket.close(); throw new IOException("元数据连接已结束"); }
        return socket;
    }

    /** 主调用超时即关闭实际套接字，不仅仅中断工作线程。 */
    @Override
    public void close() {
        closed.set(true);
        Socket socket = current.getAndSet(null);
        if (socket != null) { try { socket.close(); } catch (IOException ignored) { /* 已关闭。 */ } }
    }

    /** 此套接字只作为 Hutool/JDK HTTPS 的连接钩子，不实现另一套 HTTP 协议。 */
    private final class PinnedSocket extends Socket {
        private final McpPinnedSocketFactory owner = McpPinnedSocketFactory.this;
        private boolean targetConnected;
        /** 禁止继承 JVM SOCKS 代理。 */
        PinnedSocket() { super(Proxy.NO_PROXY); }
        /** 无论 JDK 第二次 DNS 得到什么地址，实际连接始终使用首次验证的 IP。 */
        @Override
        public void connect(SocketAddress endpoint, int requestedTimeout) throws IOException {
            if (!(endpoint instanceof InetSocketAddress target) || target.getPort() != port || closed.get()) {
                throw new IOException("元数据连接已拒绝");
            }
            int boundedTimeout = requestedTimeout > 0 ? Math.min(timeout, requestedTimeout) : timeout;
            if (proxy.type() == Proxy.Type.DIRECT) {
                super.connect(new InetSocketAddress(address, port), boundedTimeout);
            } else {
                InetSocketAddress endpointAddress = (InetSocketAddress) proxy.address();
                super.connect(new InetSocketAddress(endpointAddress.getHostString(), endpointAddress.getPort()), boundedTimeout);
                setSoTimeout(timeout);
                tunnel();
            }
            targetConnected = true;
        }

        /** 隧道只发送固定 IP 和端口，无认证、Cookie 或客户端头；响应有界且不接受重定向/认证挑战。 */
        private void tunnel() throws IOException {
            String ip = address.getHostAddress();
            String authority = (ip.contains(":") ? "[" + ip + "]" : ip) + ":" + port;
            IoUtil.write(getOutputStream(), false, ("CONNECT " + authority + " HTTP/1.1\r\nHost: " + authority + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            var headers = new StringBuilder();
            while (headers.length() < 8192) {
                int value = getInputStream().read();
                if (value < 0) { throw new IOException("元数据代理隧道未建立"); }
                headers.append((char) value);
                if (headers.length() >= 4 && headers.substring(headers.length() - 4).equals("\r\n\r\n")) {
                    String first = headers.substring(0, headers.indexOf("\r\n"));
                    if (!first.matches("HTTP/1\\.[01] 200(?: .*)?")) { throw new IOException("元数据代理拒绝隧道"); }
                    return;
                }
            }
            throw new IOException("元数据代理响应过大");
        }
        /** 任何无显式超时的连接同样有上限。 */
        @Override
        public void connect(SocketAddress endpoint) throws IOException { connect(endpoint, timeout); }
    }
}
