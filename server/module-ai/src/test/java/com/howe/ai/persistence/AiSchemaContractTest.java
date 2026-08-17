package com.howe.ai.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSchemaContractTest {
    @Test
    void phaseOneSchemaDeclaresAllFactTablesAndIdempotencyConstraints() throws Exception {
        Path sql = Path.of(System.getProperty("user.dir")).resolve("../sql/ai_admin_assistant_phase_one.sql").normalize();
        String ddl = Files.readString(sql);
        for (String table : new String[]{"ai_agent", "ai_agent_version", "ai_agent_tool", "ai_provider", "ai_channel", "ai_channel_key", "ai_model", "ai_route_policy", "ai_route_item", "ai_model_price", "ai_conversation", "ai_message", "ai_run", "ai_run_event", "ai_run_checkpoint", "ai_outbox_event", "ai_model_call", "ai_tool_call", "ai_tool_approval"}) {
            // 必须匹配到建表语句本身，避免 ai_channel 被 ai_channel_key 前缀误判。
            assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS " + table + " ("), table);
        }
        assertTrue(ddl.contains("UNIQUE KEY uk_ai_run_event_sequence"));
        assertTrue(ddl.contains("UNIQUE KEY uk_ai_outbox_idempotency"));
        assertTrue(ddl.contains("UPDATE ai_agent SET"));
    }

    @Test
    void retentionAndCleanupParametersAreDeclaredWithSafeDefaults() throws Exception {
        Path sql = Path.of(System.getProperty("user.dir")).resolve("../sql/ai_admin_assistant_phase_one.sql").normalize();
        String ddl = Files.readString(sql);
        // 事件、消息与调试内容都必须有明确留存边界，避免事实表无限增长。
        for (String key : new String[]{"ai.retention.event.days", "ai.retention.message.days",
                "ai.retention.debug.payload.days", "ai.retention.cleanup.batch"}) {
            assertTrue(ddl.contains("'" + key + "'"), key);
        }
        // 完整 Prompt/响应调试内容默认关闭，需显式开启。
        assertTrue(ddl.contains("'ai.debug.payload.enabled','false'"));
    }
}
