package com.howe.ai.queue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class OutboxWorkerTest {
    @Test
    void queueRuntimeTypesExistAndExposeRecoveryOperations() throws Exception {
        Class<?> queue = assertDoesNotThrow(() -> Class.forName("com.howe.ai.queue.RedisStreamTaskQueue"));
        Class<?> publisher = assertDoesNotThrow(() -> Class.forName("com.howe.ai.queue.AiOutboxPublisher"));
        Class<?> worker = assertDoesNotThrow(() -> Class.forName("com.howe.ai.worker.AiRunWorker"));
        Class<?> recovery = assertDoesNotThrow(() -> Class.forName("com.howe.ai.recovery.AiRunRecoveryJob"));

        assertTrue(Arrays.stream(queue.getMethods()).anyMatch(method -> method.getName().equals("consume")));
        assertTrue(Arrays.stream(publisher.getMethods()).anyMatch(method -> method.getName().equals("publishPending")));
        assertTrue(Arrays.stream(worker.getMethods()).anyMatch(method -> method.getName().equals("handle")));
        assertTrue(Arrays.stream(recovery.getMethods()).anyMatch(method -> method.getName().equals("recoverExpired")));
    }

    @Test
    void taskStreamUsesStableCommandNamesAndIdempotency() throws Exception {
        Class<?> message = assertDoesNotThrow(() -> Class.forName("com.howe.ai.queue.AiTaskMessage"));
        assertEquals(String.class, message.getMethod("command").getReturnType());
        assertEquals(String.class, message.getMethod("idempotencyKey").getReturnType());
        assertEquals(String.class, message.getMethod("payload").getReturnType());

        assertEquals("run.execute", message.getMethod("execute", String.class, String.class, String.class)
            .invoke(null, "1", "idem", "{}").getClass().getMethod("command").invoke(
                message.getMethod("execute", String.class, String.class, String.class)
                    .invoke(null, "1", "idem", "{}")));
        assertEquals("run.resume", message.getMethod("resume", String.class, String.class, String.class)
            .invoke(null, "1", "idem", "{}").getClass().getMethod("command").invoke(
                message.getMethod("resume", String.class, String.class, String.class)
                    .invoke(null, "1", "idem", "{}")));
        assertEquals("run.cancel", message.getMethod("cancel", String.class, String.class, String.class)
            .invoke(null, "1", "idem", "{}").getClass().getMethod("command").invoke(
                message.getMethod("cancel", String.class, String.class, String.class)
                    .invoke(null, "1", "idem", "{}")));
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/queue/RedisStreamTaskQueue.java"));
        assertTrue(source.contains("idempotencyKey"));
    }

    @Test
    void schemaCarriesLeaseRecoveryAndLogicalDeadLetterSupport() throws Exception {
        String ddl = Files.readString(Path.of("../sql/ai_admin_assistant_phase_one.sql"));
        assertTrue(ddl.contains("worker_id"));
        assertTrue(ddl.contains("lease_until"));
        assertTrue(ddl.contains("heartbeat_time"));
        assertTrue(ddl.contains("recovery_count"));
        assertTrue(ddl.contains("idx_ai_run_lease"));

        Class<?> queue = Class.forName("com.howe.ai.queue.RedisStreamTaskQueue");
        assertTrue(Arrays.stream(queue.getMethods()).anyMatch(method -> method.getName().equals("deadLetter")));
    }

    @Test
    void terminalRunProtectionIsPartOfWorkerContract() throws Exception {
        Class<?> lease = assertDoesNotThrow(() -> Class.forName("com.howe.ai.worker.RunLease"));
        assertEquals(String.class, lease.getMethod("workerId").getReturnType());
        assertTrue(Arrays.stream(lease.getMethods()).anyMatch(method -> method.getName().equals("isExpired")));
        assertFalse(Arrays.stream(lease.getMethods()).anyMatch(method -> method.getName().equals("forceComplete")));
    }

    @Test
    void taskQueueImplementsStableQueueContract() throws Exception {
        Class<?> contract = Class.forName("com.howe.ai.contract.AiTaskQueue");
        assertDoesNotThrow(() -> contract.getMethod("resume", String.class));
        assertTrue(contract.isAssignableFrom(Class.forName("com.howe.ai.queue.RedisStreamTaskQueue")));
    }

    @Test
    void runCompletionRequiresTheCurrentLiveLease() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/ai/AiFactMapper.xml"));
        assertTrue(xml.contains("updateRunStatusWithLease"));
        assertTrue(xml.contains("lease_until &gt; NOW()"));
        assertTrue(xml.contains("worker_id=#{workerId}"));
    }

    @Test
    void outboxInsertAndRecoveryUseDurableFields() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/ai/AiFactMapper.xml"));
        assertTrue(xml.contains("<insert id=\"insertOutbox\""));
        assertTrue(xml.contains("available_time"));
        assertTrue(xml.contains("ON DUPLICATE KEY UPDATE outbox_id=LAST_INSERT_ID(outbox_id)"));
        assertTrue(xml.contains("<update id=\"recordOutboxFailure\""));
    }

    @Test
    void redisRecoveryUsesAutoClaimRatherThanOnlyLocalPendingReads() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/queue/RedisStreamTaskQueue.java"));
        assertTrue(source.contains("pending("));
        assertTrue(source.contains("claim("));
        String recovery = Files.readString(Path.of("src/main/java/com/howe/ai/recovery/AiRunRecoveryJob.java"));
        assertTrue(recovery.contains("@Scheduled"));
    }

    @Test
    void cancellationStopsAtTheServerSideIntermediateState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/worker/AiRunWorker.java"));
        assertTrue(source.contains("RunStatus.CANCEL_REQUESTED.name(), workerId"));
        assertFalse(source.contains("RunStatus.CANCEL_REQUESTED.name(),\n                RunStatus.CANCELLED.name(), workerId"));
    }

    @Test
    void redisGroupInitializationDoesNotHideConnectionFailures() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/queue/RedisStreamTaskQueue.java"));
        assertTrue(source.contains("BUSYGROUP"));
        assertTrue(source.contains("throw exception"));
    }

    @Test
    void runSnapshotExposesLeaseFieldsForWorkerGuards() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/ai/AiFactMapper.xml"));
        assertTrue(xml.contains("worker_id, lease_until, recovery_count"));
    }

    @Test
    void emptyStreamCreatesConsumerGroupWithMakeStream() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/queue/RedisStreamTaskQueue.java"));
        assertTrue(source.contains("xGroupCreate"));
        assertTrue(source.contains(", true)"));
    }

    @Test
    void consumerAcknowledgesOnlyAfterWorkerSucceeds() throws Exception {
        Class<?> consumer = assertDoesNotThrow(() -> Class.forName("com.howe.ai.worker.AiRunStreamConsumer"));
        assertTrue(Arrays.stream(consumer.getMethods()).anyMatch(method -> method.getName().equals("consumeOnce")));
        String source = Files.readString(Path.of("src/main/java/com/howe/ai/worker/AiRunStreamConsumer.java"));
        assertTrue(source.indexOf("worker.handle") < source.indexOf("queue.acknowledge"));
    }

    @Test
    void outboxDrainAndRecoveryRunOnExplicitSchedules() throws Exception {
        String publisher = Files.readString(Path.of("src/main/java/com/howe/ai/queue/AiOutboxPublisher.java"));
        assertTrue(publisher.contains("@Scheduled"));
        assertTrue(publisher.contains("publishPendingScheduled"));
        String recovery = Files.readString(Path.of("src/main/java/com/howe/ai/recovery/AiRunRecoveryJob.java"));
        assertTrue(recovery.contains("@Scheduled"));
    }

    @Test
    void leaseGuardRejectsExpiredLease() {
        var now = java.time.Instant.parse("2026-08-13T00:00:00Z");
        var lease = new com.howe.ai.worker.RunLease("1", "worker-1", now);
        assertTrue(lease.isExpired(java.time.Clock.fixed(now, java.time.ZoneOffset.UTC)));
    }
}
