package com.howe.mcp.operation;

import com.howe.common.tool.model.ToolExecutionContext;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.common.tool.operation.ToolOperationReceipt;
import com.howe.common.tool.operation.ToolOperationRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 平台操作服务：预览、准备、确认、拒绝与查询。
 *
 * <p>身份、内部客户端登记与授权版本由平台可信作用域提供；领域只提交业务参数与幂等键。
 * 本服务不接触 MCP 协议类型，也不把确认凭证交给模型或浏览器。</p>
 */
@Slf4j
public final class McpOperationService {

    /** 准备操作有效期。 */
    private static final Duration PREPARE_TTL = Duration.ofMinutes(15);

    /** 确认上下文有效期。 */
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);

    /** 本地写排队截止时间。 */
    private static final Duration LOCAL_QUEUE_TTL = Duration.ofMinutes(5);

    /** 元数据保留时间。 */
    private static final Duration RETAIN = Duration.ofDays(30);

    /** 加密载荷保留时间。 */
    private static final Duration PAYLOAD_RETAIN = Duration.ofDays(7);

    private final McpOperationStore store;
    /** 装配完成后由收集器写入；运行期只读。 */
    private volatile Map<String, ToolOperationHandler> handlers = Map.of();

    /** 装配完成后由收集器写入；运行期只读。 */
    private volatile Map<String, com.howe.common.tool.model.ToolAccess> accesses = Map.of();
    private volatile Map<String, String> scopes = Map.of();
    private volatile Map<String, String> stepVersions = Map.of();

    /** 装配完成后写入后续步骤处理器版本，供父操作登记时冻结。 */
    public void stepVersions(Map<String, String> indexed) {
        stepVersions = Map.copyOf(indexed);
    }

    /**
     * 返回后续步骤代码键到处理器版本的只读映射。
     *
     * @return 不可变映射
     */
    public Map<String, String> stepVersions() {
        return stepVersions;
    }

    /** 装配原工具范围。 */
    public void scopes(Map<String, String> indexed) {
        scopes = Map.copyOf(indexed);
    }

    /** 原工具范围缺失时拒绝执行及结果读取。 */
    public void requireScope(McpOperationRecord record, Set<String> approved) {
        String scope = scopes.get(record.toolName() + "@" + record.toolVersion());
        if (scope == null || !approved.contains(scope)) {
            throw new com.howe.common.tool.exception.ToolException("execution_denied", "缺少原工具授权范围");
        }
    }

    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final String frontendBaseUrl;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    /**
     * 注入账本、按工具键索引的 Handler、序列化器与固定前端基址。
     *
     * @param store 账本
     * @param mapper JSON 序列化器
     * @param frontendBaseUrl 固定前端站点基址
     */
    public McpOperationService(McpOperationStore store, com.fasterxml.jackson.databind.ObjectMapper mapper,
            String frontendBaseUrl, org.springframework.transaction.support.TransactionTemplate transactions) {
        this.store = Objects.requireNonNull(store);
        this.mapper = Objects.requireNonNull(mapper);
        this.frontendBaseUrl = Objects.requireNonNull(frontendBaseUrl);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /**
     * 返回按工具键索引的 Handler，供执行器复用同一份目录。
     *
     * @return 不可变映射
     */
    public Map<String, ToolOperationHandler> handlers() {
        return handlers;
    }

    /**
     * 装配完成后写入 Handler 索引。
     *
     * @param indexed 工具键到 Handler 的不可变映射
     */
    public void handlers(Map<String, ToolOperationHandler> indexed) {
        this.handlers = Map.copyOf(indexed);
    }

    /**
     * 装配完成后写入访问要求索引。
     *
     * @param indexed 工具键到访问要求的不可变映射
     */
    public void accesses(Map<String, com.howe.common.tool.model.ToolAccess> indexed) {
        this.accesses = Map.copyOf(indexed);
    }

    /**
     * 返回按工具键索引的访问要求；来源为同一批已安装工具定义。
     *
     * @return 不可变映射
     */
    public Map<String, com.howe.common.tool.model.ToolAccess> accesses() {
        return accesses;
    }

    /**
     * 工具侧申请：预览或幂等准备。
     *
     * @param request 操作申请
     * @param context 可信业务上下文
     * @param binding 平台作用域提供的授权绑定
     * @return 通用回执
     */
    public ToolOperationReceipt request(ToolOperationRequest request, ToolExecutionContext context,
            McpOperationBinding binding) {
        ToolOperationHandler handler = handler(request.toolName(), request.toolVersion());
        if (request.mode() == ToolOperationRequest.Mode.PREPARE) {
            Optional<McpOperationRecord> existing = existing(request, binding);
            if (existing.isPresent()) {
                return existingReceipt(request, binding, handler, context, existing.get());
            }
        }
        ToolOperationContext operationContext = new ToolOperationContext(context, ToolOperationContext.Phase.PREPARE,
                null, 0, Instant.now(), null, () -> { });
        ToolOperationHandler.Plan plan = handler.plan(operationContext, request.arguments());
        if (request.mode() == ToolOperationRequest.Mode.PREVIEW) {
            return previewReceipt(plan);
        }
        return prepare(request, context, binding, handler, plan, operationContext);
    }

    /**
     * 浏览器确认视图；只返回本人与原绑定一致的操作。
     *
     * @param operationId 操作标识
     * @param userId 会话账号
     * @param binding 原授权绑定
     * @return 确认视图
     */
    public Map<String, Object> confirmationView(String operationId, Long userId, McpOperationBinding binding) {
        McpOperationRecord record = bound(operationId, userId, binding);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("operationId", record.operationId());
        view.put("toolName", record.toolName());
        view.put("targetType", record.targetType());
        view.put("targetId", record.targetId());
        view.put("expectedVersion", record.expectedVersion());
        view.put("state", record.state());
        view.put("stage", record.stage());
        view.put("rowVersion", record.rowVersion());
        view.put("prepareExpiresAt", record.prepareExpiresAt());
        view.put("confirmationExpiresAt", record.rowVersion() == 0 ? null : confirmationExpiry(record));
        view.put("canConfirm", McpOperationState.PREPARED.equals(record.state())
                && record.prepareExpiresAt() != null && record.prepareExpiresAt().isAfter(Instant.now()));
        // 安全摘要来自加密计划，只投影白名单字段，不下发正文。
        view.put("summary", safeSummary(record));
        return view;
    }

    /**
     * 追加后续步骤视图；只投影固定代码键、状态与安全结果，不含载荷或回执。
     *
     * @param view 既有操作视图
     * @param steps 父操作登记的步骤
     */
    public void putSteps(Map<String, Object> view, List<McpOperationStepRecord> steps) {
        if (steps == null || steps.isEmpty()) {
            view.put("steps", List.of());
            return;
        }
        List<Map<String, Object>> items = new java.util.ArrayList<>(steps.size());
        for (McpOperationStepRecord step : steps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepKey", step.stepKey());
            item.put("state", step.state());
            item.put("stage", step.stage());
            item.put("resultCode", step.resultCode());
            item.put("createTime", step.createTime());
            item.put("updateTime", step.updateTime());
            items.add(item);
        }
        view.put("steps", items);
    }

    /**
     * 从加密计划中读取安全摘要；不可解密时拒绝确认。
     *
     * @param record 账本记录
     * @return 安全摘要
     */
    private Map<String, Object> safeSummary(McpOperationRecord record) {
        return plan(record).safeSummary();
    }

    /**
     * 创建确认上下文：固定摘要与会话绑定，期限不随刷新延长。
     *
     * @param operationId 操作标识
     * @param userId 会话账号
     * @param binding 原授权绑定
     * @param expectedRowVersion 期望行版本
     * @param summaryHash 本工具计划的安全摘要哈希
     * @param sessionHash 浏览器会话绑定哈希
     * @return 确认上下文
     */
    public Map<String, Object> openConfirmation(String operationId, Long userId, McpOperationBinding binding,
            long expectedRowVersion, byte[] summaryHash, byte[] sessionHash) {
        McpOperationRecord record = bound(operationId, userId, binding);
        if (!McpOperationState.PREPARED.equals(record.state())) {
            throw new com.howe.common.tool.exception.ToolException("state_conflict", "操作状态已变化");
        }
        Instant expiresAt = confirmationExpiry(record);
        if (expiresAt == null) {
            expiresAt = min(Instant.now().plus(CONFIRMATION_TTL), record.prepareExpiresAt());
        }
        if (!store.openConfirmationContext(operationId, userId, expectedRowVersion, summaryHash, sessionHash, expiresAt)) {
            throw new com.howe.common.tool.exception.ToolException("state_conflict", "操作状态已变化，请刷新后重试");
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("operationId", operationId);
        view.put("summaryHash", java.util.HexFormat.of().formatHex(summaryHash));
        view.put("confirmationExpiresAt", expiresAt);
        view.put("rowVersion", expectedRowVersion + 1);
        return view;
    }

    /**
     * 浏览器确认并原子入队。
     *
     * @param operationId 操作标识
     * @param userId 会话账号
     * @param binding 原授权绑定
     * @param expectedRowVersion 期望行版本
     * @param summaryHash 提交的摘要哈希
     * @param now 当前时间
     * @return 确认结果
     */
    public Map<String, Object> confirm(String operationId, Long userId, McpOperationBinding binding,
            long expectedRowVersion, String summaryHash, byte[] sessionHash, Instant now) {
        McpOperationRecord record = bound(operationId, userId, binding);
        if (!McpOperationState.PREPARED.equals(record.state())) {
            // 重复确认返回既有状态，不第二次派发。
            return Map.of("operationId", operationId, "state", record.state());
        }
        byte[] hash = parseHex(summaryHash);
        byte[] confirmationHash = new byte[32];
        new java.security.SecureRandom().nextBytes(confirmationHash);
        Duration queueTtl = ToolOperationHandler.QueueGroup.BLOG_PUBLICATION.name().equals(record.queueGroup())
                ? Duration.ofMinutes(15) : LOCAL_QUEUE_TTL;
        // 领域认领与确认状态必须在同一事务：任一步失败都不能留下半占用。
        Boolean confirmed = transactions.execute(status -> {
            ToolOperationHandler handler = handlers.get(record.toolName() + "@" + record.toolVersion());
            if (handler != null) {
                ToolOperationHandler.Plan plan = plan(record);
                ToolOperationContext context = new ToolOperationContext(
                        new ToolExecutionContext(String.valueOf(userId), record.preparedDeptId() == null ? null
                                : String.valueOf(record.preparedDeptId()), "confirm-" + operationId),
                        ToolOperationContext.Phase.CONFIRM, operationId, record.fence(), now,
                        now.plus(queueTtl), () -> { });
                handler.revalidate(context, plan);
                handler.reserve(context, plan);
            }
            boolean changed = store.confirm(operationId, userId, expectedRowVersion, hash, confirmationHash,
                    sessionHash, now.plus(queueTtl), now);
            if (!changed) {
                status.setRollbackOnly();
            }
            return changed;
        });
        if (!Boolean.TRUE.equals(confirmed)) {
            McpOperationRecord latest = bound(operationId, userId, binding);
            if (!McpOperationState.PREPARED.equals(latest.state())) {
                return Map.of("operationId", operationId, "state", latest.state());
            }
            throw new com.howe.common.tool.exception.ToolException("confirmation_expired", "确认已过期，请重新预览");
        }
        return Map.of("operationId", operationId, "state", McpOperationState.QUEUED);
    }

    /**
     * 拒绝操作；仅尚未确认的 PREPARED 可取消。
     *
     * @param operationId 操作标识
     * @param userId 会话账号
     * @param binding 原授权绑定
     * @return 拒绝结果
     */
    public Map<String, Object> reject(String operationId, Long userId, McpOperationBinding binding, long expectedRowVersion) {
        McpOperationRecord record = bound(operationId, userId, binding);
        if (!McpOperationState.PREPARED.equals(record.state())) {
            throw new com.howe.common.tool.exception.ToolException("state_conflict", "操作状态已变化，无法拒绝");
        }
        boolean rejected = store.cancelPrepared(operationId, userId, expectedRowVersion);
        if (!rejected) {
            throw new com.howe.common.tool.exception.ToolException("state_conflict", "操作已被处理");
        }
        return Map.of("operationId", operationId, "state", McpOperationState.CANCELLED);
    }

    /**
     * 查询本人操作；结果正文是否投影由原工具权限复核决定。
     *
     * @param operationId 操作标识
     * @param userId 会话账号
     * @param binding 原授权绑定
     * @return 操作视图
     */
    public Map<String, Object> operationView(String operationId, Long userId, McpOperationBinding binding) {
        McpOperationRecord record = bound(operationId, userId, binding);
        return view(record);
    }

    /**
     * 分页查询本人操作。
     *
     * @param userId 会话账号
     * @param page 页码
     * @param pageSize 每页条数
     * @param state 可选状态
     * @return 分页结果
     */
    public Map<String, Object> page(Long userId, int page, int pageSize, String state) {
        long offset = (long) (page - 1) * pageSize;
        List<Map<String, Object>> items = store.pageOwned(userId, state, offset, pageSize).stream()
                .map(McpOperationService::view).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", store.countOwned(userId, state));
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 稳定视图。 */
    private static Map<String, Object> view(McpOperationRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("operationId", record.operationId());
        view.put("toolName", record.toolName());
        view.put("state", record.state());
        view.put("stage", record.stage());
        view.put("resultCode", record.resultCode());
        view.put("createdAt", record.createdAt());
        view.put("finishedAt", record.finishedAt());
        return view;
    }

    /** 预览回执不创建操作。 */
    private ToolOperationReceipt previewReceipt(ToolOperationHandler.Plan plan) {
        return new ToolOperationReceipt(null, ToolOperationReceipt.State.PREVIEW, "PREVIEW", false, null, null,
                Instant.now(), null, null, plan.safeSummary(), null, null);
    }

    /** 幂等准备。 */
    private ToolOperationReceipt prepare(ToolOperationRequest request, ToolExecutionContext context,
            McpOperationBinding binding, ToolOperationHandler handler, ToolOperationHandler.Plan plan,
            ToolOperationContext operationContext) {
        byte[] idempotencyHash = McpOperationStoreImpl.sha256(request.idempotencyKey());
        byte[] argumentsHash = requestHash(request.arguments());
        Optional<McpOperationRecord> existing = existing(request, binding);
        if (existing.isPresent()) {
            return existingReceipt(request, binding, handler, context, existing.get());
        }
        Instant now = operationContext.evaluationTime();
        String operationId = McpOperationStoreImpl.newOperationId();
        McpOperationRecord record = new McpOperationRecord(operationId, binding.userId(),
                binding.registeredClientId(), binding.grantId(), binding.grantVersion(), binding.resourceUri(),
                String.join(",", binding.scopes()), context.deptId() == null ? null : Long.valueOf(context.deptId()),
                binding.accountContextHash(), request.toolName(),
                request.toolVersion(), plan.executionKind().name(), plan.queueGroup().name(), idempotencyHash,
                argumentsHash, McpOperationStoreImpl.sha256(plan.configurationDigest() == null ? ""
                        : plan.configurationDigest()), plan.targetType(), plan.targetId(), plan.expectedVersion(),
                McpOperationState.PREPARED, "PREPARED", null, null, null, now.plus(PREPARE_TTL), null, now, null,
                null, 0, 0, null, null, 0, now.plus(PAYLOAD_RETAIN), now.plus(RETAIN), null, now, now);
        try {
            store.insertPrepared(record, canonical(request.toolName(), plan));
        } catch (IllegalStateException exception) {
            // 唯一键并发冲突：回到幂等查询，绝不复用不同参数。
            return existing(request, binding)
                    .map(found -> existingReceipt(request, binding, handler, context, found))
                    .orElseThrow(() -> exception);
        }
        return receipt(record, plan);
    }

    /** 按原申请身份定位已有操作，不依赖变化中的领域快照。 */
    private Optional<McpOperationRecord> existing(ToolOperationRequest request, McpOperationBinding binding) {
        return store.findByIdempotency(binding.userId(), binding.registeredClientId(), request.toolName(),
                request.toolVersion(), McpOperationStoreImpl.sha256(request.idempotencyKey()));
    }

    /** 复用已有操作；旧版账本仍按原计划哈希核对。 */
    private ToolOperationReceipt existingReceipt(ToolOperationRequest request, McpOperationBinding binding,
            ToolOperationHandler handler, ToolExecutionContext context, McpOperationRecord record) {
        if (!binding.grantId().equals(record.grantId()) || binding.grantVersion() != record.grantVersion()
                || !Objects.equals(binding.resourceUri(), record.resourceUri())) {
            throw new com.howe.common.tool.exception.ToolException("authorization_changed", "原授权已变化");
        }
        ToolOperationHandler.Plan savedPlan = plan(record);
        boolean same = java.util.Arrays.equals(record.argumentsHash(), requestHash(request.arguments()))
                || savedPlan.arguments().equals(request.arguments());
        if (!same) {
            // 兼容此前把实时摘要也计入参数哈希的账本记录。
            ToolOperationContext retryContext = new ToolOperationContext(context, ToolOperationContext.Phase.PREPARE,
                    null, 0, Instant.now(), null, () -> { });
            ToolOperationHandler.Plan currentPlan = handler.plan(retryContext, request.arguments());
            same = java.util.Arrays.equals(record.argumentsHash(),
                    McpOperationStoreImpl.sha256(canonical(request.toolName(), currentPlan)));
        }
        if (!same) {
            throw new com.howe.common.tool.exception.ToolException("idempotency_key_reused", "幂等键已用于其它参数");
        }
        return receipt(record, savedPlan);
    }

    /** 请求业务参数哈希不包含随时间变化的安全摘要与对象状态。 */
    private byte[] requestHash(Map<String, Object> arguments) {
        try {
            String json = mapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(arguments);
            return McpOperationStoreImpl.sha256(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new com.howe.common.tool.exception.ToolException("invalid_arguments", "操作参数无法规范化");
        }
    }

    /** 账本记录投影为回执。 */
    private ToolOperationReceipt receipt(McpOperationRecord record, ToolOperationHandler.Plan plan) {
        return new ToolOperationReceipt(record.operationId(), ToolOperationReceipt.State.valueOf(record.state()),
                record.stage(), McpOperationState.PREPARED.equals(record.state()), confirmationUri(record.operationId()),
                record.prepareExpiresAt(), record.createdAt(), record.finishedAt(), record.resultCode(),
                plan.safeSummary(), null, null);
    }

    /** 确认页地址始终来自固定前端站点。 */
    private URI confirmationUri(String operationId) {
        String base = frontendBaseUrl.replaceAll("/+$", "");
        return URI.create(base + "/mcp/confirm?operationId=" + operationId);
    }

    /** 按工具键查 Handler，缺失即安装期问题，运行期拒绝。 */
    private ToolOperationHandler handler(String toolName, String toolVersion) {
        ToolOperationHandler handler = handlers.get(toolName + "@" + toolVersion);
        if (handler == null) {
            throw new com.howe.common.tool.exception.ToolException("tool_unavailable", "工具不可用");
        }
        return handler;
    }

    /** 读取本人且与原绑定一致的操作。 */
    private McpOperationRecord bound(String operationId, Long userId, McpOperationBinding binding) {
        return store.findBound(operationId, userId, binding.grantId(), binding.registeredClientId())
                .orElseThrow(() -> new com.howe.common.tool.exception.ToolException("not_found_or_forbidden",
                        "操作不存在或无权访问"));
    }

    /** 规范化计划：键排序 JSON，用于摘要与加密载荷。 */
    private String canonical(String toolName, ToolOperationHandler.Plan plan) {
        try {
            Map<String, Object> wrapper = new java.util.TreeMap<>();
            wrapper.put("format", "mcp-operation-plan-v1");
            wrapper.put("toolName", toolName);
            wrapper.put("targetType", plan.targetType());
            wrapper.put("targetId", plan.targetId());
            wrapper.put("expectedVersion", plan.expectedVersion());
            wrapper.put("configurationDigest", plan.configurationDigest());
            wrapper.put("executionKind", plan.executionKind().name());
            wrapper.put("queueGroup", plan.queueGroup().name());
            wrapper.put("safeSummary", new java.util.TreeMap<>(plan.safeSummary()));
            wrapper.put("arguments", new java.util.TreeMap<>(plan.arguments()));
            return mapper.writeValueAsString(wrapper);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new com.howe.common.tool.exception.ToolException("invalid_arguments", "操作参数无法规范化");
        }
    }

    /** 完成原工具权限复核后读取安全结果。 */
    Map<String, Object> safeResult(McpOperationRecord record) {
        String json = store.decryptedResult(record.operationId());
        if (json == null) return null;
        try {
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new com.howe.common.tool.exception.ToolException("invalid_result", "操作结果无法读取");
        }
    }

    /** 从账本加密载荷还原计划；格式标识随载荷一起校验。 */
    ToolOperationHandler.Plan plan(McpOperationRecord record) {
        String json = store.decryptedRequest(record.operationId());
        try {
            Map<String, Object> wrapper = mapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            if (!"mcp-operation-plan-v1".equals(wrapper.get("format"))) {
                throw new com.howe.common.tool.exception.ToolException("invalid_result", "操作计划格式不受支持");
            }
            return new ToolOperationHandler.Plan(cast(wrapper.get("arguments")), cast(wrapper.get("safeSummary")),
                    record.targetType(), record.targetId(), record.expectedVersion(),
                    wrapper.get("configurationDigest") == null ? null : String.valueOf(wrapper.get("configurationDigest")),
                    ToolOperationHandler.ExecutionKind.valueOf(record.executionKind()),
                    ToolOperationHandler.QueueGroup.valueOf(record.queueGroup()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new com.howe.common.tool.exception.ToolException("invalid_result", "操作计划无法恢复");
        }
    }

    /** 类型安全 Map 转换。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** 已固定确认期限时不再延长。 */
    private static Instant confirmationExpiry(McpOperationRecord record) {
        return record.confirmationExpiresAt();
    }

    /** 取较早时间。 */
    private static Instant min(Instant left, Instant right) {
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    /** 十六进制摘要解析。 */
    private static byte[] parseHex(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new com.howe.common.tool.exception.ToolException("invalid_arguments", "摘要格式不正确");
        }
        return java.util.HexFormat.of().parseHex(value);
    }
}
