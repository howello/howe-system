package com.howe.ai.runtime;

import java.time.Clock;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.howe.ai.contract.ModelDelta;
import com.howe.ai.contract.ModelGateway;
import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ModelStreamResult;
import com.howe.ai.contract.ModelUsage;
import com.howe.ai.contract.ProviderErrorCategory;
import com.howe.ai.contract.RunBudgetSnapshot;
import com.howe.ai.event.RunEventEmitter;

/**
 * 阶段一有限单轮 Harness：每个模型边界检查取消、deadline、调用和输出预算。
 *
 * <p>事件序号由 {@link RunEventEmitter} 背后的 MySQL 事实源分配；Harness 只维护步骤号用于生成
 * 幂等键，重复执行同一步骤不会产生重复事件。</p>
 */
@Service
public class ConfigAgentRuntime {
    private final ModelGateway gateway;
    private final RunEventEmitter events;
    private final Clock clock;

    @Autowired
    public ConfigAgentRuntime(ModelGateway gateway, RunEventEmitter events) {
        this(gateway, events, Clock.systemUTC());
    }

    public ConfigAgentRuntime(ModelGateway gateway, RunEventEmitter events, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "模型网关不能为空");
        this.events = Objects.requireNonNull(events, "事件发射器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public RuntimeResult run(String runId, ModelRequest request, RunBudgetSnapshot budget, BooleanSupplier cancelled) {
        BudgetGuard guard = new BudgetGuard(budget, clock);
        if (cancelled.getAsBoolean()) return RuntimeResult.failure(ProviderErrorCategory.PERMISSION_DENIED);
        // Harness 不直接改变数据库状态，调用方以 CANCEL_REQUESTED 作为协作取消信号。
        final String CANCEL_REQUESTED = "CANCEL_REQUESTED";
        String checkpoint = "checkpoint";
        int step = 0;
        emit(runId, ++step, "run.started", "{}");
        emit(runId, ++step, "run.checkpoint", "{\"checkpoint\":\"checkpoint\"}");
        if (!guard.allowModelCall()) return RuntimeResult.failure(ProviderErrorCategory.UNKNOWN);
        ModelStreamResult response = gateway.stream(request);
        if (!response.success()) {
            if (response.partial()) {
                emit(runId, ++step, "stream.interrupted", "{\"category\":\"" + response.errorCategory() + "\"}");
            } else {
                emit(runId, ++step, "run.failed", "{\"category\":\"" + response.errorCategory() + "\"}");
            }
            return RuntimeResult.failure(response.errorCategory());
        }

        StringBuilder content = new StringBuilder();
        for (ModelDelta delta : response.deltas()) {
            if (cancelled.getAsBoolean() || guard.expired()) {
                emit(runId, ++step, "run.cancelled", "{}");
                return RuntimeResult.failure(ProviderErrorCategory.PERMISSION_DENIED);
            }
            content.append(delta.content());
            emit(runId, ++step, "message.delta", "{\"index\":" + delta.index() + ",\"content\":\""
                + escape(delta.content()) + "\"}");
        }
        recordUsage(runId, ++step, guard, response);
        if (!guard.withinOutputBudget()) {
            emit(runId, ++step, "run.failed", "{\"category\":\"BUDGET_EXCEEDED\"}");
            return RuntimeResult.failure(ProviderErrorCategory.UNKNOWN);
        }
        emit(runId, ++step, "run.completed", "{\"checkpoint\":\"" + checkpoint + "\"}");
        return RuntimeResult.success(content.toString());
    }

    /**
     * 如实记录用量：Provider 上报时用真实 token 数，未上报时退回可测量的字符数并标记不可精算。
     * 阶段一没有价格快照，因此两种情况下成本都不可精算，绝不写成零成本。
     */
    private void recordUsage(String runId, int step, BudgetGuard guard, ModelStreamResult response) {
        int outputChars = response.deltas().stream().mapToInt(delta -> delta.content().length()).sum();
        if (response.usageReported()) {
            ModelUsage usage = response.usage();
            guard.recordModelCallWithoutCost(usage.completionTokens());
            emit(runId, step, "run.usage", "{\"computable\":true,\"promptTokens\":" + usage.promptTokens()
                + ",\"completionTokens\":" + usage.completionTokens() + ",\"costComputable\":false}");
            return;
        }
        guard.recordModelCallWithoutCost(outputChars);
        emit(runId, step, "run.usage",
            "{\"computable\":false,\"outputChars\":" + outputChars + ",\"costComputable\":false}");
    }

    private void emit(String runId, int step, String type, String payload) {
        events.publish(runId, type, payload, runId + ":" + step);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
