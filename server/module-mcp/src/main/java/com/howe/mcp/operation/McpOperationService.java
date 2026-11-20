package com.howe.mcp.operation;

import com.howe.common.tool.model.ToolExecutionContext;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.common.tool.operation.ToolOperationReceipt;
import com.howe.common.tool.operation.ToolOperationRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * 平台操作服务：预览、直接入队与查询。
 *
 * <p>身份、内部客户端登记与授权版本由平台可信作用域提供；领域只提交业务参数与幂等键。
 * 本服务不接触 MCP 协议类型。</p>
 */
@Slf4j
public final class McpOperationService {

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

    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    /**
     * 注入账本、按工具键索引的 Handler、序列化器与事务。
     *
     * @param store 账本
     * @param mapper JSON 序列化器
     */
    public McpOperationService(McpOperationStore store, com.fasterxml.jackson.databind.ObjectMapper mapper,
            org.springframework.transaction.support.TransactionTemplate transactions) {
        this.store = Objects.requireNonNull(store);
        this.mapper = Objects.requireNonNull(mapper);
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
     * 工具侧申请：预览或幂等提交。
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
        return enqueue(request, context, binding, handler, plan, operationContext);
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

    /** 本人操作的基本状态投影。 */
    static Map<String, Object> view(McpOperationRecord record) {
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

    /** 入队与领域资源预留在同一事务完成，重复提交沿用原记录。 */
    private ToolOperationReceipt enqueue(ToolOperationRequest request, ToolExecutionContext context,
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
        Duration queueTtl = plan.queueGroup() == ToolOperationHandler.QueueGroup.BLOG_PUBLICATION
                ? Duration.ofMinutes(15) : LOCAL_QUEUE_TTL;
        Instant deadline = now.plus(queueTtl);
        McpOperationRecord record = new McpOperationRecord(operationId, binding.userId(),
                binding.registeredClientId(), binding.grantId(), binding.grantVersion(), binding.resourceUri(),
                context.deptId() == null ? null : Long.valueOf(context.deptId()),
                binding.accountContextHash(), request.toolName(),
                request.toolVersion(), plan.executionKind().name(), plan.queueGroup().name(), idempotencyHash,
                argumentsHash, McpOperationStoreImpl.sha256(plan.configurationDigest() == null ? ""
                        : plan.configurationDigest()), plan.targetType(), plan.targetId(), plan.expectedVersion(),
                McpOperationState.QUEUED, "QUEUED", deadline, now, null,
                null, 0, 0, null, null, 0, now.plus(PAYLOAD_RETAIN), now.plus(RETAIN), null, now, now);
        try {
            transactions.executeWithoutResult(status -> {
                store.insertQueued(record, canonical(request.toolName(), plan));
                var reservation = new ToolOperationContext(context, ToolOperationContext.Phase.CONFIRM,
                        operationId, record.fence(), now, deadline, () -> { });
                handler.revalidate(reservation, plan);
                handler.reserve(reservation, plan);
            });
        } catch (org.springframework.dao.DuplicateKeyException exception) {
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
                record.stage(), false, null,
                null, record.createdAt(), record.finishedAt(), record.resultCode(),
                plan.safeSummary(), null, null);
    }

    /** 按工具键查 Handler，缺失即安装期问题，运行期拒绝。 */
    private ToolOperationHandler handler(String toolName, String toolVersion) {
        ToolOperationHandler handler = handlers.get(toolName + "@" + toolVersion);
        if (handler == null) {
            throw new com.howe.common.tool.exception.ToolException("tool_unavailable", "工具不可用");
        }
        return handler;
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


}
