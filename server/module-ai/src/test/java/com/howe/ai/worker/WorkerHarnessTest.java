package com.howe.ai.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.ProviderErrorCategory;
import com.howe.ai.event.AiFactEventStore;
import com.howe.ai.gateway.StubModelGateway;
import com.howe.ai.gateway.StubResponse;
import com.howe.ai.persistence.AiFactPersistenceService;
import com.howe.ai.queue.AiTaskMessage;
import com.howe.ai.runtime.ConfigAgentRuntime;
import com.howe.ai.support.InMemoryFactMapper;

/**
 * Worker 与 Harness 的联动契约。
 *
 * <p>此前 Worker 只领租约并把状态改成 RUNNING 就 ACK，从不调用 Harness，
 * Run 会永远停在 RUNNING：没有模型调用、没有事件、也不会收敛到终态。</p>
 */
class WorkerHarnessTest {
    private static final String BUDGET = "{\"maxDurationSeconds\":30,\"maxModelCalls\":2,\"maxToolCalls\":1,"
        + "\"maxOutputTokens\":100,\"maxEstimatedCost\":1000,\"maxFallbackAttempts\":1}";
    private static final String ROUTE = "{\"model\":\"stub-model\"}";

    @Test
    void workerExecutesHarnessAndDrivesRunToSucceeded() {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(1L, "QUEUED", BUDGET, ROUTE);
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("统计", "完成"));
        AiRunWorker worker = worker(facts, gateway);

        boolean handled = worker.handle(AiTaskMessage.execute("1", "idem-1", "{}"), "worker-1", Duration.ofMinutes(1));

        assertTrue(handled, "执行成功后必须允许 ACK");
        assertEquals("SUCCEEDED", facts.status(1L), "Run 必须收敛到终态而不是停在 RUNNING");
        assertTrue(facts.eventTypes().contains("run.completed"), "事件必须落库：" + facts.eventTypes());
        assertTrue(facts.eventTypes().contains("message.delta"));
    }

    @Test
    void workerMarksRunFailedWhenModelReturnsNonRetryableError() {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(2L, "QUEUED", BUDGET, ROUTE);
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.failure(ProviderErrorCategory.AUTHENTICATION));
        AiRunWorker worker = worker(facts, gateway);

        worker.handle(AiTaskMessage.execute("2", "idem-2", "{}"), "worker-1", Duration.ofMinutes(1));

        assertEquals("FAILED", facts.status(2L));
        assertTrue(facts.eventTypes().contains("run.failed"));
    }

    @Test
    void terminalRunIsNeverReExecuted() {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(3L, "SUCCEEDED", BUDGET, ROUTE);
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("不应执行"));
        AiRunWorker worker = worker(facts, gateway);

        boolean handled = worker.handle(AiTaskMessage.execute("3", "idem-3", "{}"), "worker-1", Duration.ofMinutes(1));

        assertFalse(handled);
        assertEquals("SUCCEEDED", facts.status(3L));
        assertEquals(List.of(), facts.eventTypes());
    }

    @Test
    void cancelCommandStopsAtCancelRequestedWithoutRunningTheModel() {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(4L, "RUNNING", BUDGET, ROUTE);
        StubModelGateway gateway = new StubModelGateway();
        gateway.enqueue(StubResponse.success("不应执行"));
        AiRunWorker worker = worker(facts, gateway);

        worker.handle(AiTaskMessage.cancel("4", "idem-4", "{}"), "worker-1", Duration.ofMinutes(1));

        assertEquals("CANCEL_REQUESTED", facts.status(4L));
        assertEquals(List.of(), facts.eventTypes(), "取消指令不得触发模型调用");
    }

    private static AiRunWorker worker(InMemoryFactMapper facts, StubModelGateway gateway) {
        AiFactPersistenceService persistence = new AiFactPersistenceService(facts.asMapper());
        ConfigAgentRuntime runtime = new ConfigAgentRuntime(gateway, new AiFactEventStore(persistence)::append);
        return new AiRunWorker(persistence, runtime);
    }
}
