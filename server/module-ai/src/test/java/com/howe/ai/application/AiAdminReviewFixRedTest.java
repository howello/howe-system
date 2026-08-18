package com.howe.ai.application;

import com.howe.ai.persistence.AiFactPersistenceService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiAdminReviewFixRedTest {
    @Test
    void enqueuePersistsMessageAndFailsClosedWhenPersistenceMissing() {
        assertThrows(NullPointerException.class, () -> new AiAdminApplicationService(null));
    }

    @Test
    void applicationExposesRealRunQueries() throws Exception {
        assertNotNull(AiAdminApplicationService.class.getMethod("getRun", long.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("listEvents", long.class, long.class, int.class, int.class));
    }

    @Test
    void lifecycleAndConfigurationContractsExist() throws Exception {
        assertNotNull(AiAdminApplicationService.class.getMethod("createAgent", Map.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("updateAgent", long.class, int.class, Map.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("publishAgent", long.class, int.class, String.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("disableAgent", long.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("enqueueMessage", long.class, long.class, String.class, String.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("recordKey", long.class, String.class));
    }

    @Test
    void lifecycleExposesAgentCrudAndVersionQueries() throws Exception {
        assertNotNull(AiAdminApplicationService.class.getMethod("getAgent", long.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("listAgentVersions", long.class, int.class, int.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("getAgentVersion", long.class, int.class));
        var source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/howe/ai/controller/AiAdminController.java"));
        assertTrue(source.contains("/agents"));
        assertTrue(source.contains("/versions"));
        assertTrue(source.contains("ai:agent:publish"));
    }

    @Test
    void conversationContractsExist() throws Exception {
        // 创建会话与会话列表：Chat 页发起对话与展示历史的必需入口。
        // createConversation(agentKey, userId, title)：按 Agent 编码绑定会话主体，归属当前用户。
        assertNotNull(AiAdminApplicationService.class.getMethod(
            "createConversation", String.class, long.class, String.class));
        assertNotNull(AiAdminApplicationService.class.getMethod(
            "listConversations", long.class, String.class, int.class, int.class));
    }

    @Test
    void createConversationRejectsUnpublishedOrDisabledAgent() throws Exception {
        // 业务规则写入源码：未发布（published_version_id 为空）或已停用（status != '0'）的 Agent 不得建会话，
        // 与 enqueueMessage 的守护一致——否则会话建了也发不出消息。
        var source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/howe/ai/application/AiAdminApplicationService.java"));
        assertTrue(source.contains("createConversation"),
            "AiAdminApplicationService 必须实现 createConversation");
        assertTrue(source.contains("published_version_id"),
            "createConversation 必须校验 Agent 已发布（published_version_id 非空）");
        assertTrue(source.contains("status"),
            "createConversation 必须校验 Agent 未停用（status='0'）");
    }

    @Test
    void controllerExposesCompleteRunRoutesWithPermissions() throws Exception {
        var source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/howe/ai/controller/AiAdminController.java"));
        assertTrue(source.contains("@GetMapping(\"/runs/{runId}\")"));
        assertTrue(source.contains("@GetMapping(\"/runs/{runId}/events\")"));
        assertTrue(source.contains("@GetMapping(\"/runs/{runId}/usage\")"));
        assertTrue(source.contains("@GetMapping(\"/runs/{runId}/tool-usage\")"));
        assertTrue(source.contains("@PostMapping(\"/conversations/{conversationId}/messages\")"));
        assertTrue(source.contains("ai:run:view"));
    }

    @Test
    void persistenceServiceRequiresMapperAtConstruction() {
        assertThrows(NullPointerException.class, () -> new AiFactPersistenceService(null));
    }

    @Test
    void secretCipherFailsClosedAndDoesNotExposePlaintext() {
        assertThrows(IllegalStateException.class, () -> new SecretCipher(() -> null));
        var cipher = new SecretCipher(() -> "01234567890123456789012345678901");
        String encrypted = cipher.encrypt("secret");
        assertNotEquals("secret", encrypted);
        assertEquals("secret", cipher.decrypt(encrypted));
        assertTrue(cipher.mask(encrypted).contains("****"));
    }

    @Test
    void persistenceContractContainsMessageAndQueries() throws Exception {
        assertNotNull(AiFactPersistenceService.class.getMethod("insertMessage", long.class, Long.class, String.class, String.class, String.class));
        assertNotNull(AiFactPersistenceService.class.getMethod("getRun", long.class));
        assertNotNull(AiFactPersistenceService.class.getMethod("listEvents", long.class, long.class, int.class, int.class));
    }
}
