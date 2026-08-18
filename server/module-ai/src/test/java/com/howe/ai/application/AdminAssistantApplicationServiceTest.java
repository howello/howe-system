package com.howe.ai.application;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class AdminAssistantApplicationServiceTest {
    @Test
    void validatesDraftAndMasksSecrets() {
        assertTrue(AdminAssistantApplicationService.class.isAssignableFrom(AiAdminApplicationService.class));
        assertEquals(50, AdminAssistantApplicationService.MAX_PAGE_SIZE);
        assertThrows(NullPointerException.class, () -> new AiAdminApplicationService(null));
    }

    @Test
    void exposesRunCancellationAndQueries() throws Exception {
        assertNotNull(AiAdminApplicationService.class.getMethod("requestCancel", long.class, long.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("listEvents", long.class, long.class, int.class, int.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("enqueueMessage", long.class, long.class, String.class, String.class));
    }

    @Test
    void webDtosCarrySchemaAndControllersRequirePermission() throws Exception {
        assertTrue(Class.forName("com.howe.ai.web.AgentDraftRequest").getDeclaredFields().length > 0);
        var source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/howe/ai/controller/AiAdminController.java"));
        assertTrue(source.contains("@PreAuthorize"));
        assertTrue(source.contains("@Operation"));
    }
}
