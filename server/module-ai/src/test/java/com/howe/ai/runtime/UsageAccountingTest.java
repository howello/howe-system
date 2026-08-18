package com.howe.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.AiRunEvent;
import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ModelUsage;
import com.howe.ai.contract.RunBudgetSnapshot;
import com.howe.ai.event.RecordingEventEmitter;
import com.howe.ai.gateway.StubModelGateway;
import com.howe.ai.gateway.StubResponse;

/**
 * 用量与成本的如实记账。
 *
 * <p>此前 Harness 把增量的字符长度同时当成 token 数和成本喂给预算：
 * 既不是真实用量，也把「无法精算」伪装成了一个具体数字。设计要求 usage 缺失时
 * 明确标记不可精算，而不是计为零成本。</p>
 */
class UsageAccountingTest {
    private static final ModelRequest REQUEST = new ModelRequest("stub-model", "hello", Map.of());
    private static final RunBudgetSnapshot BUDGET = new RunBudgetSnapshot(30, 2, 1, 100, 1000, 1);

    @Test
    void missingProviderUsageIsMarkedNotComputableInsteadOfZeroCost() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("abc"));
        RecordingEventEmitter emitter = new RecordingEventEmitter();

        new ConfigAgentRuntime(gateway, emitter).run("run-1", REQUEST, BUDGET, () -> false);

        String usage = usageEvent(emitter);
        assertTrue(usage.contains("\"computable\":false"), "缺失 usage 必须标记不可精算：" + usage);
        assertFalse(usage.contains("\"cost\":0"), "不得把无法精算伪装成零成本：" + usage);
    }

    @Test
    void providerReportedUsageIsRecordedAsComputable() {
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.successWithUsage(new ModelUsage(11, 7), "abc"));
        RecordingEventEmitter emitter = new RecordingEventEmitter();

        new ConfigAgentRuntime(gateway, emitter).run("run-2", REQUEST, BUDGET, () -> false);

        String usage = usageEvent(emitter);
        assertTrue(usage.contains("\"computable\":true"), usage);
        assertTrue(usage.contains("\"completionTokens\":7"), usage);
        assertTrue(usage.contains("\"promptTokens\":11"), usage);
    }

    @Test
    void budgetGuardKnowsCostIsNotPreciselyComputable() {
        BudgetGuard guard = new BudgetGuard(BUDGET, java.time.Clock.systemUTC());
        guard.recordModelCallWithoutCost(12);

        assertFalse(guard.costComputable(), "没有用量或价格快照时成本不可精算");
        assertTrue(guard.withinOutputBudget(), "输出上限仍按可测量的数量兜底");
    }

    private static String usageEvent(RecordingEventEmitter emitter) {
        return emitter.events().stream().filter(event -> event.type().equals("run.usage"))
            .map(AiRunEvent::payload).findFirst()
            .orElseThrow(() -> new AssertionError("必须产生 run.usage 事件：" + emitter.events()));
    }
}
