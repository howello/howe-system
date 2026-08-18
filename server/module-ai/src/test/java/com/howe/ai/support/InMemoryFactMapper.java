package com.howe.ai.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.howe.ai.persistence.AiFactMapper;

/**
 * 内存版事实 Mapper，按 SQL 的条件更新语义模拟 Run 状态机与事件表，
 * 让 Worker、Harness 与事件流可以在没有 MySQL 的情况下被真实验证。
 */
public final class InMemoryFactMapper implements InvocationHandler {
    private final Map<Long, Map<String, Object>> runs = new LinkedHashMap<>();
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final Map<Long, Map<String, Object>> contexts = new LinkedHashMap<>();

    public static InMemoryFactMapper create() {
        return new InMemoryFactMapper();
    }

    public AiFactMapper asMapper() {
        return (AiFactMapper) Proxy.newProxyInstance(AiFactMapper.class.getClassLoader(),
            new Class[]{AiFactMapper.class}, this);
    }

    public void seedRun(long runId, String status, String budgetSnapshot, String routeSnapshot) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("run_id", runId);
        run.put("status", status);
        run.put("worker_id", null);
        run.put("lease_until", null);
        run.put("recovery_count", 0);
        runs.put(runId, run);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("route_snapshot", routeSnapshot);
        context.put("budget_snapshot", budgetSnapshot);
        context.put("system_prompt", "你是管理员助手");
        context.put("user_prompt", "统计一下文章数量");
        contexts.put(runId, context);
    }

    /** 直接写入事件表，供事件流补发测试准备历史数据。 */
    public void seedEvent(long runId, String eventType, String payload) {
        insertEvent(runId, events.size() + 1L, eventType, payload, runId + ":" + (events.size() + 1));
    }

    public void setStatus(long runId, String status) {
        runs.get(runId).put("status", status);
    }

    public String status(long runId) {
        return String.valueOf(runs.get(runId).get("status"));
    }

    public List<String> eventTypes() {
        return events.stream().map(event -> String.valueOf(event.get("event_type"))).toList();
    }

    public List<String> eventIdempotencyKeys() {
        return events.stream().map(event -> String.valueOf(event.get("idempotency_key"))).toList();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "selectRunSnapshot" -> runs.get((long) args[0]);
            case "selectRunExecutionContext" -> contexts.get((long) args[0]);
            case "claimRunLease" -> claimLease((long) args[0], (String) args[1], (Instant) args[2], (String) args[3]);
            case "updateRunStatusWithLease" -> updateWithLease((long) args[0], (String) args[1], (String) args[2],
                (String) args[3]);
            case "renewRunLease" -> 1;
            case "lockRun" -> 0L;
            case "selectNextEventSequence" -> (long) events.size() + 1;
            case "insertEvent" -> insertEvent((long) args[0], (long) args[1], (String) args[2], (String) args[3],
                (String) args[4]);
            case "selectRunEventsAfter" -> eventsAfter((long) args[0], (long) args[1], (int) args[2]);
            case "selectEventSequenceByIdempotency" -> sequenceByIdempotency((long) args[0], (String) args[1]);
            case "insertCheckpoint" -> 1;
            default -> defaultValue(method.getReturnType());
        };
    }

    /** 对应 SQL：仅当状态匹配且租约未被他人持有时才抢到。 */
    private int claimLease(long runId, String workerId, Instant leaseUntil, String expectedStatus) {
        Map<String, Object> run = runs.get(runId);
        if (run == null || !expectedStatus.equals(run.get("status"))) return 0;
        run.put("worker_id", workerId);
        run.put("lease_until", leaseUntil);
        return 1;
    }

    /** 对应 SQL：终态不可逆，且必须持有当前有效租约。 */
    private int updateWithLease(long runId, String expectedStatus, String status, String workerId) {
        Map<String, Object> run = runs.get(runId);
        if (run == null || !expectedStatus.equals(run.get("status"))) return 0;
        if (!workerId.equals(run.get("worker_id"))) return 0;
        run.put("status", status);
        return 1;
    }

    /** 对应唯一键 (run_id, idempotency_key)：与 MySQL 一致，重复写入直接冲突。 */
    private int insertEvent(long runId, long sequenceNo, String eventType, String eventJson, String idempotencyKey) {
        if (sequenceByIdempotency(runId, idempotencyKey) != null) {
            throw new IllegalStateException("Duplicate entry for key uk_ai_run_event_idempotency");
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("run_id", runId);
        event.put("sequence_no", sequenceNo);
        event.put("event_type", eventType);
        event.put("event_json", eventJson);
        event.put("idempotency_key", idempotencyKey);
        event.put("create_time", Timestamp.from(Instant.EPOCH.plusSeconds(sequenceNo)));
        events.add(event);
        return 1;
    }

    private Long sequenceByIdempotency(long runId, String idempotencyKey) {
        return events.stream()
            .filter(event -> event.get("run_id").equals(runId)
                && String.valueOf(event.get("idempotency_key")).equals(idempotencyKey))
            .map(event -> ((Number) event.get("sequence_no")).longValue())
            .findFirst().orElse(null);
    }

    private List<Map<String, Object>> eventsAfter(long runId, long sequence, int limit) {
        return events.stream()
            .filter(event -> event.get("run_id").equals(runId)
                && ((Number) event.get("sequence_no")).longValue() > sequence)
            .limit(limit).toList();
    }

    private static Object defaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        if (type == List.class) return List.of();
        return null;
    }
}
