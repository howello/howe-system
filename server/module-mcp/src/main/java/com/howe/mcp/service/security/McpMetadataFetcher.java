package com.howe.mcp.service.security;

import cn.hutool.core.io.IoUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpConnection;
import cn.hutool.http.Method;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** CIMD 出站边界：Hutool 负责 HTTP，JSSE 钩子固定目标；四个工作线程且不排队，整体期限包含 DNS。 */
@Slf4j
public final class McpMetadataFetcher implements AutoCloseable {
    private static final int MAX_BYTES = 16384;
    private final ObjectReader reader;
    private final Resolver resolver;
    private final Exchange exchange;
    private final Function<URI, Proxy> routes;
    private final Duration deadline;
    private final SSLSocketFactory tls;
    private final ThreadPoolExecutor workers;

    /** 生产使用平台信任库、系统 DNS 与下述固定地址传输，不采用 Hutool 默认宽松证书工厂。 */
    public McpMetadataFetcher(ObjectMapper mapper) {
        this(mapper, InetAddress::getAllByName, McpMetadataFetcher::http, Duration.ofSeconds(5),
                uri -> selectProxy(ProxySelector.getDefault(), uri));
    }

    /** 网络边界可替换用于组件检查；线程和整体期限仍使用真实实现。 */
    McpMetadataFetcher(ObjectMapper mapper, Resolver resolver, Exchange exchange, Duration deadline) {
        this(mapper, resolver, exchange, deadline, uri -> Proxy.NO_PROXY);
    }

    /** 代理选择同样位于有界工作线程内；组件检查不依赖运行机器的实际代理配置。 */
    McpMetadataFetcher(ObjectMapper mapper, Resolver resolver, Exchange exchange, Duration deadline, Function<URI, Proxy> routes) {
        if (deadline.isZero() || deadline.isNegative() || deadline.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("元数据获取期限无效");
        }
        this.resolver = resolver; this.exchange = exchange; this.deadline = deadline; this.routes = routes;
        ObjectMapper bounded = mapper.copy();
        bounded.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                .maxStringLength(4096).maxNumberLength(64).build());
        reader = bounded.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            tls = context.getSocketFactory();
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("TLS 信任配置不可用"); }
        workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "mcp-metadata"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 返回经校验的 JSON 和缓存指令；确定错误、超时或解析失败均不缓存。 */
    public Document fetch(String clientId) {
        URI uri;
        try { uri = McpUriPolicy.requireMetadataUri(clientId); }
        catch (IllegalArgumentException exception) { throw new McpMetadataException(false); }
        var attempt = new Attempt();
        var future = new FutureTask<>(() -> retrieve(uri, attempt));
        try {
            workers.execute(future);
            return future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException | TimeoutException | CancellationException exception) {
            throw new McpMetadataException(true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new McpMetadataException(true);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof McpMetadataException failure) { throw failure; }
            throw new McpMetadataException(true);
        } finally {
            attempt.close(); future.cancel(true);
        }
    }

    /** 在同一个期限内解析全部地址，任意特殊地址使整个主机被拒绝，不挑选混合答案中的公网地址放行。 */
    private Document retrieve(URI uri, Attempt attempt) {
        try {
            String host = uri.getHost();
            if (host.startsWith("[") && host.endsWith("]")) { host = host.substring(1, host.length() - 1); }
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses == null || addresses.length == 0 || addresses.length > 16
                    || Arrays.stream(addresses).anyMatch(address -> !McpUriPolicy.isPublicAddress(address))) {
                throw new McpMetadataException(false);
            }
            if (attempt.closed.get()) { throw new McpMetadataException(true); }
            var sockets = new McpPinnedSocketFactory(tls, host, addresses[0], uri.getPort() == -1 ? 443 : uri.getPort(), 2000,
                    routes.apply(uri));
            attempt.sockets.set(sockets);
            if (attempt.closed.get()) { sockets.close(); throw new McpMetadataException(true); }
            Reply reply = exchange.get(uri, sockets);
            if (reply.status() != 200) { throw new McpMetadataException(reply.status() >= 500 || reply.status() == 429); }
            if (reply.body() == null || reply.body().length > MAX_BYTES
                    || !jsonType(reply.contentType())) { throw new McpMetadataException(false); }
            java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(reply.body()));
            JsonNode document = reader.readValue(reply.body());
            if (document == null || !document.isObject() || !document.path("client_id").isTextual()
                    || !uri.toString().equals(document.path("client_id").textValue())) { throw new McpMetadataException(false); }
            return new Document(document, cacheSeconds(reply.cacheControl(), reply.age()));
        } catch (McpMetadataException exception) { throw exception; }
        catch (com.fasterxml.jackson.core.JacksonException | java.nio.charset.CharacterCodingException | IllegalArgumentException exception) { throw new McpMetadataException(false); }
        catch (IOException | RuntimeException exception) { throw new McpMetadataException(true); }
    }

    /** 使用系统选择的第一条路由，不在代理失败时绕过它直连；只支持无需凭据的 HTTP CONNECT。 */
    static Proxy selectProxy(ProxySelector selector, URI uri) {
        if (selector == null) { return Proxy.NO_PROXY; }
        List<Proxy> selected = selector.select(uri);
        if (selected == null || selected.isEmpty() || selected.get(0) == null) { throw new McpMetadataException(true); }
        Proxy proxy = selected.get(0);
        if (proxy.type() == Proxy.Type.DIRECT) { return Proxy.NO_PROXY; }
        if (proxy.type() != Proxy.Type.HTTP || !(proxy.address() instanceof InetSocketAddress endpoint)
                || endpoint.getPort() < 1 || endpoint.getHostString().isBlank()) { throw new McpMetadataException(true); }
        return proxy;
    }

    /** HTTP 外层保持无代理，系统代理隧道由固定地址套接字建立；不跟随重定向或使用全局凭据。 */
    static Reply http(URI uri, McpPinnedSocketFactory sockets) throws IOException {
        if (CookieHandler.getDefault() != null) { throw new IOException("不支持全局 CookieHandler 的元数据请求"); }
        HttpConnection connection = HttpConnection.create(uri.toURL(), Proxy.NO_PROXY)
                .setMethod(Method.GET).setHttpsInfo(HttpsURLConnection.getDefaultHostnameVerifier(), sockets)
                .setConnectTimeout(2000).setReadTimeout(2000).setInstanceFollowRedirects(false).disableCache()
                .header(Header.ACCEPT, "application/json", true).header(Header.ACCEPT_ENCODING, "identity", true)
                .header(Header.CONNECTION, "close", true);
        connection.getHttpURLConnection().setAuthenticator(new Authenticator() { });
        try {
            int status = connection.responseCode();
            if (status != 200) { return new Reply(status, null, null, null, null); }
            String encoding = header(connection, "Content-Encoding", false);
            if (encoding != null && !"identity".equalsIgnoreCase(encoding)) { throw new McpMetadataException(false); }
            long length = connection.getHttpURLConnection().getContentLengthLong();
            if (length > MAX_BYTES) { throw new McpMetadataException(false); }
            byte[] body = IoUtil.readBytes(connection.getInputStream(), MAX_BYTES + 1);
            return new Reply(status, header(connection, "Content-Type", false), header(connection, "Cache-Control", true),
                    header(connection, "Age", true), body);
        } finally { connection.disconnectQuietly(); }
    }

    /** 缓存指令合并全部同名头，不能让后一个 max-age 覆盖先前的 no-store；单值头拒绝重复。 */
    private static String header(HttpConnection connection, String name, boolean combine) {
        List<String> values = connection.headers().entrySet().stream()
                .filter(entry -> entry.getKey() != null && name.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()).toList();
        if (values.isEmpty()) { return null; }
        if (!combine && values.size() != 1) { throw new McpMetadataException(false); }
        return String.join(",", values);
    }

    /** 只接受明确 JSON 类型，不接受 HTML、脚本或任意通配媒体类型。 */
    private static boolean jsonType(String value) {
        if (value == null) { return false; }
        MediaType type = MediaType.parseMediaType(value);
        return "application".equals(type.getType()) && ("json".equals(type.getSubtype()) || type.getSubtype().endsWith("+json"))
                && !type.isWildcardSubtype() && (type.getCharset() == null || java.nio.charset.StandardCharsets.UTF_8.equals(type.getCharset()));
    }

    /** 缺省十分钟，尊重禁存/重验证与上游 Age；有歧义或非法指令时不复用文档。 */
    static int cacheSeconds(String cacheControl, String age) {
        long seconds = 600;
        boolean maximumSeen = false;
        if (cacheControl != null) {
            if (cacheControl.length() > 2048) { return 0; }
            for (String raw : cacheControl.split(",")) {
                String directive = raw.trim().toLowerCase(java.util.Locale.ROOT);
                String name = directive.split("=", 2)[0].trim();
                if (List.of("no-store", "no-cache", "private").contains(name)) { return 0; }
                if ("max-age".equals(name)) {
                    if (maximumSeen) { return 0; }
                    maximumSeen = true;
                    String[] parts = directive.split("=", 2);
                    if (parts.length != 2 || !parts[1].trim().matches("([0-9]{1,9}|\"[0-9]{1,9}\")")) { return 0; }
                    seconds = Math.min(600, Long.parseLong(parts[1].trim().replace("\"", "")));
                }
            }
        }
        if (age != null) {
            if (!age.matches("[0-9]{1,9}")) { return 0; }
            seconds -= Long.parseLong(age);
        }
        return (int) Math.max(0, seconds);
    }

    /** 单次返回有界 JSON，不向调用者暴露网络响应、URL 或套接字。 */
    public record Document(JsonNode json, int cacheSeconds) { }
    /** 仅用于获取器内部的网络边界替换。 */
    record Reply(int status, String contentType, String cacheControl, String age, byte[] body) { }
    /** 系统 DNS 查询边界。 */
    @FunctionalInterface
    interface Resolver { /** 返回当前全部解析结果。 */ InetAddress[] resolve(String host) throws IOException; }
    /** Hutool HTTP 请求边界，生产只使用上述固定地址实现。 */
    @FunctionalInterface
    interface Exchange { /** 使用当前连接工厂获取一次文档，不执行转发。 */ Reply get(URI uri, McpPinnedSocketFactory sockets) throws IOException; }

    /** 关闭与 DNS 返回/连接创建竞争时，迟到的套接字不能再次接入。 */
    private static final class Attempt implements AutoCloseable {
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicReference<McpPinnedSocketFactory> sockets = new AtomicReference<>();
        /** 标记结束后关闭已经出现的实际网络连接。 */
        @Override
        public void close() {
            closed.set(true);
            var value = sockets.getAndSet(null);
            if (value != null) { value.close(); }
        }
    }

    /** 拒绝新获取并中断已有工作，所有任务自身仍受整体期限和连接关闭约束。 */
    @Override
    public void close() { workers.shutdownNow(); }
}
