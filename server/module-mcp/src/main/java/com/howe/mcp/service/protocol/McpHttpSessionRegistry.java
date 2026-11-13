package com.howe.mcp.service.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.howe.mcp.dto.McpJsonRpcError;
import com.howe.mcp.service.McpBearerAuthenticationService;
import com.howe.mcp.service.security.McpBearerIdentity;
import com.howe.mcp.service.security.McpExecutionIdentity;
import com.howe.mcp.service.security.McpInvocationContext;
import com.howe.mcp.service.security.McpResourceFilter;
import com.howe.mcp.tool.McpToolRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

/** 使用 SDK 公开会话 SPI 的有界 WebMVC Streamable HTTP 适配，目录和连接严格按授权隔离。 */
@Slf4j
public final class McpHttpSessionRegistry implements AutoCloseable {
    public static final List<String> VERSIONS = List.of("2024-11-05", "2025-03-26", "2025-06-18");
    private final McpToolRegistry tools;
    private final McpBearerAuthenticationService authentication;
    private final Clock clock;
    private final int maximum;
    private final int perUser;
    private final Duration idle;
    // 协议能力是开放集合；独立 SDK mapper 忽略未知扩展，不改变应用或 OAuth 的严格解析策略。
    private final McpJsonMapper mapper = protocolMapper();

    /** 协议独立映射器同时支持开放能力集合及操作回执的 ISO 时间。 */
    static McpJsonMapper protocolMapper() {
        return new JacksonMcpJsonMapper(new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Long, Integer> owners = new HashMap<>();
    private final Semaphore streams = new Semaphore(32);
    private final ScheduledExecutorService cleaner;
    private int reserved;
    private boolean closing;

    /** 会话数量、每用户数量与空闲期限在启动时固定，工具执行仍使用共享的有界线程池。 */
    public McpHttpSessionRegistry(McpToolRegistry tools, McpBearerAuthenticationService authentication,
            Clock clock, int maximum, int perUser, Duration idle) {
        if (maximum < 1 || maximum > 4096 || perUser < 1 || perUser > 64 || perUser > maximum
                || idle.compareTo(Duration.ofSeconds(30)) < 0 || idle.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("MCP 协议会话限制无效");
        }
        this.tools = tools;
        this.authentication = authentication;
        this.clock = clock;
        this.maximum = maximum;
        this.perUser = perUser;
        this.idle = idle;
        cleaner = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "mcp-session-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        cleaner.scheduleWithFixedDelay(this::reap, 5, 5, TimeUnit.SECONDS);
    }

    /** 每次由资源过滤器传入当前认证，SDK Session ID 自身永远不是认证凭据。 */
    public ServerResponse handle(ServerRequest request) {
        Object proof = request.attributes().get(McpBearerIdentity.REQUEST_ATTRIBUTE);
        if (!(proof instanceof McpBearerIdentity identity)) {
            return error(401, null, -32001, "需要访问令牌");
        }
        JsonNode json = (JsonNode) request.attributes().get(McpResourceFilter.MESSAGE_ATTRIBUTE);
        Object id = id(json);
        try {
            String sessionId = request.headers().firstHeader("Mcp-Session-Id");
            if (json != null && "initialize".equals(json.path("method").asText())) {
                return sessionId == null ? initialize(request, identity, json) : error(400, id, -32600, "请建立新的协议会话");
            }
            if (sessionId == null) {
                return error(400, id, -32600, "需要协议会话标识");
            }
            Entry entry = entries.get(sessionId);
            if (entry == null || !entry.binding.equals(Binding.of(identity.invocation()))) {
                return error(404, id, -32001, "协议会话不存在");
            }
            List<McpSchema.Tool> current = tools.visibleTools(identity.invocation());
            if (!entry.catalog.equals(current)) {
                remove(sessionId, entry);
                return error(404, id, -32001, "工具目录已变化，请重新初始化");
            }
            String protocol = request.headers().firstHeader("MCP-Protocol-Version");
            if (protocol != null && !entry.protocol.equals(protocol)) {
                return error(400, id, -32600, "协议版本不匹配");
            }
            entry.proof = identity;
            entry.touched = clock.instant();
            if ("DELETE".equals(request.methodName())) {
                remove(sessionId, entry);
                return ServerResponse.ok().build();
            }
            if ("GET".equals(request.methodName())) {
                if (!accepts(request, MediaType.TEXT_EVENT_STREAM) || request.headers().firstHeader("Last-Event-ID") != null) {
                    return error(400, id, -32600, "该流不提供历史回放");
                }
                return listen(entry);
            }
            if (json == null || !accepts(request, MediaType.APPLICATION_JSON) || !accepts(request, MediaType.TEXT_EVENT_STREAM)) {
                return error(400, id, -32600, "请求需接受 JSON 和 SSE");
            }
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(mapper, json.toString());
            McpTransportContext transport = context(identity);
            if (message instanceof McpSchema.JSONRPCNotification notification) {
                if ("notifications/initialized".equals(notification.method())) {
                    entry.initialized.set(true);
                    entry.provider.session.accept(notification).contextWrite(value -> value.put(McpTransportContext.KEY, transport))
                            .block(Duration.ofSeconds(5));
                }
                // 未注册的通知按 JSON-RPC 忽略；不能触发 SDK 默认 roots 回调或记录原始通知正文。
                return ServerResponse.accepted().build();
            }
            if (message instanceof McpSchema.JSONRPCResponse) {
                return error(400, null, -32600, "本服务未发起对应请求");
            }
            var rpc = (McpSchema.JSONRPCRequest) message;
            if (!entry.initialized.get() && !"ping".equals(rpc.method())) {
                return error(400, id, -32000, "协议初始化尚未完成");
            }
            if (!Set.of("ping", "tools/list", "tools/call", "logging/setLevel").contains(rpc.method())) {
                return error(200, id, -32601, "不支持该方法");
            }
            JsonNode params = json.path("params");
            if ("tools/list".equals(rpc.method()) && params.hasNonNull("cursor")
                    || "tools/call".equals(rpc.method()) && (!label(params.path("name"))
                            || params.has("arguments") && !params.get("arguments").isObject())
                    || "logging/setLevel".equals(rpc.method()) && !Set.of("debug", "info", "notice", "warning", "error",
                            "critical", "alert", "emergency").contains(params.path("level").asText())) {
                return error(200, id, -32602, "方法参数无效");
            }
            return response(entry, rpc, transport);
        } catch (Exception exception) {
            // 只记录代码元数据，不能打印异常消息、请求正文或携带凭据的完整异常链。
            log.warn("MCP 协议请求未完成：异常={}，位置={}，原因类型={}", exception.getClass().getName(),
                    exception.getStackTrace().length == 0 ? "unknown" : exception.getStackTrace()[0],
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getName());
            return error(503, id, -32603, "协议服务暂不可用");
        }
    }

    /** 先保留容量，再创建仅有当前可见工具的 SDK 服务；初始化失败归还全部容量。 */
    private ServerResponse initialize(ServerRequest request, McpBearerIdentity identity, JsonNode json) throws Exception {
        JsonNode params = json.path("params");
        if (id(json) == null || !params.isObject() || !params.path("protocolVersion").isTextual()
                || !params.path("protocolVersion").textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !params.path("capabilities").isObject() || !label(params.path("clientInfo").path("name"))
                || !label(params.path("clientInfo").path("version"))
                || !accepts(request, MediaType.APPLICATION_JSON) || !accepts(request, MediaType.TEXT_EVENT_STREAM)) {
            return error(400, id(json), -32602, "初始化参数无效");
        }
        String header = request.headers().firstHeader("MCP-Protocol-Version");
        if (header != null && !VERSIONS.contains(header)) {
            return error(400, id(json), -32600, "不支持该 HTTP 协议版本");
        }
        McpSchema.InitializeRequest initialization;
        try {
            initialization = mapper.convertValue(params, new TypeRef<McpSchema.InitializeRequest>() { });
        } catch (IllegalArgumentException exception) {
            return error(400, id(json), -32602, "初始化参数无效");
        }
        if (!reserve(identity.invocation().userId())) {
            return error(429, id(json), -32000, "协议会话已达上限");
        }
        Entry entry = null;
        McpSyncServer created = null;
        boolean stored = false;
        try {
            var specifications = tools.specifications(identity.invocation());
            var provider = new Provider();
            McpSyncServer server = McpServer.sync(provider).jsonMapper(mapper).serverInfo("howe-mcp", "1.0.0")
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                    .tools(specifications).requestTimeout(Duration.ofSeconds(10)).build();
            created = server;
            var init = provider.factory.startSession(initialization);
            provider.session = init.session();
            var result = init.initResult().contextWrite(value -> value.put(McpTransportContext.KEY, context(identity)))
                    .block(Duration.ofSeconds(5));
            if (result == null) { throw new IllegalStateException("SDK 未返回初始化结果"); }
            entry = new Entry(Binding.of(identity.invocation()), specifications.stream().map(specification -> specification.tool()).toList(),
                    provider, server, result.protocolVersion(), identity, clock.instant());
            synchronized (this) {
                if (closing) { throw new IllegalStateException("协议服务正在关闭"); }
                entries.put(init.session().getId(), entry);
                stored = true;
            }
            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).header("Mcp-Session-Id", init.session().getId())
                    .body(new McpSchema.JSONRPCResponse("2.0", id(json), result, null));
        } finally {
            if (!stored) {
                release(identity.invocation().userId());
                if (entry != null) { entry.close(); }
                else if (created != null) { created.closeGracefully(); }
            }
        }
    }

    /** POST 响应采用标准 SSE，不发送 event id，避免宣称 SDK 尚未实现的历史回放。 */
    private ServerResponse response(Entry entry, McpSchema.JSONRPCRequest rpc, McpTransportContext context) {
        return ServerResponse.sse(builder -> {
            Stream stream = attach(entry, builder, rpc.id());
            if (stream == null) { return; }
            try {
                entry.provider.session.responseStream(rpc, stream)
                        .contextWrite(value -> value.put(McpTransportContext.KEY, context)).block(Duration.ofSeconds(70));
            } catch (RuntimeException exception) {
                log.error("MCP 协议执行失败：类型={}，位置={}，原因类型={}", exception.getClass().getName(),
                        exception.getStackTrace().length == 0 ? "unknown" : exception.getStackTrace()[0],
                        exception.getCause() == null ? "none" : exception.getCause().getClass().getName());
                stream.sendMessage(new McpSchema.JSONRPCResponse("2.0", rpc.id(), null,
                        new McpSchema.JSONRPCResponse.JSONRPCError(-32603, "协议请求未完成", null))).block();
            } finally {
                stream.close();
            }
        }, Duration.ofSeconds(75));
    }

    /** 独立监听流最长 60 秒并可重新连接；一个会话同时只保留一个 GET 监听。 */
    private ServerResponse listen(Entry entry) {
        return ServerResponse.sse(builder -> {
            synchronized (entry) {
                Stream previous = entry.listening.getAndSet(null);
                if (previous != null) { previous.close(); }
                Stream stream = attach(entry, builder, null);
                if (stream == null) { return; }
                entry.listening.set(stream);
                entry.provider.session.listeningStream(stream);
            }
        }, Duration.ofSeconds(60));
    }

    /** 活动 SSE 流同时受全局 32 和单会话 4 的上限约束，完成/超时都归还许可。 */
    private Stream attach(Entry entry, ServerResponse.SseBuilder builder, Object requestId) {
        synchronized (entry) {
            if (entry.closed.get() || entry.active.size() >= 4 || !streams.tryAcquire()) {
                try { builder.data(mapper.writeValueAsString(new McpJsonRpcError(requestId, -32000, "服务繁忙"))); }
                catch (Exception exception) { log.warn("MCP 响应发送未完成"); }
                builder.complete();
                return null;
            }
            var stream = new Stream(builder, entry);
            entry.active.add(stream);
            builder.onComplete(stream::close).onTimeout(stream::close).onError(error -> stream.close());
            return stream;
        }
    }

    /** 初始化中的对象也计入配额，防止并发初始化绕过上限。 */
    private synchronized boolean reserve(Long userId) {
        if (closing || reserved >= maximum || owners.getOrDefault(userId, 0) >= perUser) { return false; }
        reserved++;
        owners.merge(userId, 1, Integer::sum);
        return true;
    }

    /** 归还与用户关联的容量，不保留无限增长的用户键。 */
    private synchronized void release(Long userId) {
        reserved--;
        owners.computeIfPresent(userId, (key, count) -> count <= 1 ? null : count - 1);
    }

    /** 仅移除仍为同一对象的会话，不影响使用其他 Bearer 的合法独立会话。 */
    private void remove(String id, Entry expected) {
        if (entries.remove(id, expected)) {
            release(expected.binding.userId());
            expected.close();
        }
    }

    /** 定时清除过期、撤销、停用及目录已变化的连接，数据库故障时关闭连接而非继续信任缓存。 */
    public void reap() {
        for (var row : List.copyOf(entries.entrySet())) {
            Entry entry = row.getValue();
            boolean active = entry.touched.plus(idle).isAfter(clock.instant())
                    && (entry.initialized.get() || entry.created.plusSeconds(30).isAfter(clock.instant()))
                    && authentication.isCurrent(entry.proof);
            if (active) {
                try { active = entry.catalog.equals(tools.visibleTools(entry.proof.invocation())); }
                catch (RuntimeException exception) { active = false; }
            }
            if (!active) { remove(row.getKey(), entry); }
        }
    }

    /** 关闭全部活动流及 SDK 会话，避免依赖 SDK 未完成的请求流清理逻辑。 */
    @Override
    public void close() {
        synchronized (this) { closing = true; }
        cleaner.shutdownNow();
        List.copyOf(entries.entrySet()).forEach(row -> remove(row.getKey(), row.getValue()));
    }

    /** 不将 SDK 异常数据或原报文带回客户端。 */
    private static ServerResponse error(int status, Object id, int code, String message) {
        return ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).body(new McpJsonRpcError(id, code, message));
    }

    /** JSON-RPC ID 只保留已经在输入边界校验过的字符串或整数。 */
    private static Object id(JsonNode json) {
        if (json == null || !json.has("id")) { return null; }
        return json.get("id").isTextual() ? json.get("id").textValue() : json.get("id").longValue();
    }

    /** 允许标准媒体类型的合法参数形式，不将通配符误认为双方协议协商。 */
    private static boolean accepts(ServerRequest request, MediaType type) {
        return request.headers().accept().stream().anyMatch(value -> value.getType().equals(type.getType())
                && value.getSubtype().equals(type.getSubtype()));
    }

    /** 客户端标识展示字段有界且不包含控制字符，不获取任何客户端外部资源。 */
    private static boolean label(JsonNode value) {
        return value.isTextual() && !value.textValue().isBlank() && value.textValue().length() <= 128
                && value.textValue().codePoints().noneMatch(Character::isISOControl);
    }

    /** 不从 Servlet 线程的 SecurityContext 延迟读取身份。 */
    private static McpTransportContext context(McpBearerIdentity identity) {
        return McpTransportContext.create(Map.of(McpExecutionIdentity.TRANSPORT_KEY, identity.invocation()));
    }

    /** Session 绑定 grant 与 scope，不绑定会轮换的 access token，也不保存完整 LoginUser。 */
    private record Binding(Long userId, String clientId, Long grantId, long version, Set<String> scopes, String resource) {
        /** 从每次认证的当前服务端上下文建立隔离键。 */
        private static Binding of(McpInvocationContext value) {
            return new Binding(value.userId(), value.clientId(), value.grantId(), value.authorizationVersion(), value.scopes(), value.resource());
        }
    }

    /** 一条 SDK 会话只持有本授权筛选的工具规格，目录不在不同用户间共享。 */
    private final class Entry {
        final Binding binding;
        final List<McpSchema.Tool> catalog;
        final Provider provider;
        final McpSyncServer server;
        final String protocol;
        final Set<Stream> active = ConcurrentHashMap.newKeySet();
        final AtomicBoolean initialized = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();
        volatile McpBearerIdentity proof;
        volatile Instant touched;
        final Instant created;
        final AtomicReference<Stream> listening = new AtomicReference<>();

        /** 不保存访问令牌明文，后续每次请求都更新其独立认证证明。 */
        Entry(Binding binding, List<McpSchema.Tool> catalog, Provider provider, McpSyncServer server, String protocol,
                McpBearerIdentity proof, Instant touched) {
            this.binding = binding; this.catalog = catalog; this.provider = provider; this.server = server;
            this.protocol = protocol; this.proof = proof; this.touched = touched;
            this.created = touched;
        }

        /** 关闭所有已登记流，再关闭 SDK 会话；重复关闭幂等。 */
        void close() {
            List<Stream> snapshot;
            synchronized (this) {
                if (!closed.compareAndSet(false, true)) { return; }
                snapshot = List.copyOf(active);
            }
            snapshot.forEach(Stream::close);
            server.closeGracefully();
        }
    }

    /** 只使用公开 Factory，初始化和 JSON-RPC 方法分派仍由 SDK 完成。 */
    private static final class Provider implements McpStreamableServerTransportProvider {
        McpStreamableServerSession.Factory factory;
        McpStreamableServerSession session;
        /** SDK 构建时提供其会话工厂。 */
        @Override
        public void setSessionFactory(McpStreamableServerSession.Factory factory) { this.factory = factory; }
        /** 与锁定的 SDK 0.17.0 支持列表一致。 */
        @Override
        public List<String> protocolVersions() { return VERSIONS; }
        /** 通知只发送给此单一授权会话。 */
        @Override
        public Mono<Void> notifyClients(String method, Object params) { return session == null ? Mono.empty() : session.sendNotification(method, params); }
        /** 外围已关闭全部活动流，此处释放 SDK 监听状态。 */
        @Override
        public Mono<Void> closeGracefully() { return Mono.fromRunnable(() -> { if (session != null) { session.close(); } }); }
    }

    /** 不发送 event id 的 SSE 流；协议错误去除内部 data，结束时精确归还并发许可。 */
    private final class Stream implements McpStreamableServerTransport {
        private final ServerResponse.SseBuilder builder;
        private final Entry owner;
        private final AtomicBoolean closed = new AtomicBoolean();
        /** 保存本流的响应目标，不持有原始请求或明文凭据。 */
        Stream(ServerResponse.SseBuilder builder, Entry owner) { this.builder = builder; this.owner = owner; }
        /** 所有协议响应统一经过有界工具结果与错误脱敏。 */
        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.fromRunnable(() -> {
                synchronized (this) {
                    if (closed.get()) { return; }
                    McpSchema.JSONRPCMessage safe = message;
                    if (message instanceof McpSchema.JSONRPCResponse response && response.error() != null) {
                        safe = new McpSchema.JSONRPCResponse("2.0", response.id(), null,
                                new McpSchema.JSONRPCResponse.JSONRPCError(response.error().code(), "协议请求未完成", null));
                    }
                    try { builder.event("message").data(mapper.writeValueAsString(safe)); }
                    catch (Exception exception) { throw new IllegalStateException("MCP 响应发送失败"); }
                }
            });
        }
        /** 本实现不提供回放，SDK 提供的内部事件 ID 不作为可恢复游标发送。 */
        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String eventId) { return sendMessage(message); }
        /** 原生 SDK 类型转换保持标准记录和字段契约。 */
        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> type) { return mapper.convertValue(data, type); }
        /** 标准异步关闭适配。 */
        @Override
        public Mono<Void> closeGracefully() { return Mono.fromRunnable(this::close); }
        /** 完成、超时、断连和会话撤销都只归还一次许可。 */
        @Override
        public synchronized void close() {
            if (closed.compareAndSet(false, true)) {
                owner.active.remove(this);
                owner.listening.compareAndSet(this, null);
                streams.release();
                try { builder.complete(); }
                catch (RuntimeException exception) { log.warn("MCP 响应流已经结束"); }
            }
        }
    }
}
