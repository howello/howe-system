package com.howe.ai.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AiFactPersistenceReviewFixTest {
    private static final String XML = readXml();

    private static String readXml() {
        try {
            return Files.readString(Path.of("src/main/resources/mapper/ai/AiFactMapper.xml"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void createRunReturnsRunIdAndDoesNotAcceptCallerSequence() throws Exception {
        var create = AiFactPersistenceService.class.getMethod("createRun", long.class, long.class, long.class,
                String.class, String.class, String.class, String.class);
        assertEquals(long.class, create.getReturnType());
        var append = AiFactPersistenceService.class.getMethod("appendEvent", long.class, String.class, String.class, String.class);
        assertEquals(long.class, append.getReturnType());
        assertTrue(XML.contains("useGeneratedKeys=\"true\"") && XML.contains("keyProperty=\"runId\""));
    }

    @Test
    void runCreationUsesUniqueKeyAsStableIdempotency() {
        assertTrue(XML.contains("ON DUPLICATE KEY UPDATE run_id=LAST_INSERT_ID(run_id)"));
    }

    @Test
    void eventSequenceIsAllocatedUnderRunRowLock() {
        assertTrue(XML.contains("FROM ai_run WHERE run_id=#{runId} FOR UPDATE"));
        assertTrue(XML.contains("MAX(sequence_no)"));
    }

    @Test
    void runFactQueriesRequireConversationOwnership() {
        assertTrue(XML.contains("JOIN ai_conversation c ON c.conversation_id=r.conversation_id"));
        assertTrue(XML.contains("c.user_id=#{userId} OR #{userId}=1"));
        assertTrue(XML.contains("WHERE e.run_id=#{runId}"));
        assertTrue(XML.contains("WHERE m.run_id=#{runId}"));
        assertTrue(XML.contains("WHERE t.run_id=#{runId}"));
    }

    @Test
    void draftsCannotChangeAfterPublishedAndVersionsStartUnpublished() {
        assertTrue(XML.contains("published_version_id IS NULL"));
        assertTrue(XML.contains("published_time=NOW()"));
        assertTrue(XML.contains("published_time)"));
    }

    @Test
    void agentVersionBoundaryOnlyPublishesAndReads() throws Exception {
        assertNotNull(AiFactPersistenceService.class.getMethod("publishAgentVersion", long.class, int.class,
                String.class, String.class, String.class, String.class, String.class));
        assertNotNull(AiFactPersistenceService.class.getMethod("getAgentVersion", long.class, int.class));
        assertThrows(NoSuchMethodException.class, () -> AiFactPersistenceService.class.getMethod("updateAgentVersion",
                long.class, int.class, String.class));
    }
}
