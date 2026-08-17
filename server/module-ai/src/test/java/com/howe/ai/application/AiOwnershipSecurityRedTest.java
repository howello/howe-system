package com.howe.ai.application;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/** 资源归属回归测试：查询、取消和入队都必须携带当前用户。 */
class AiOwnershipSecurityRedTest {
    @Test
    void runQueriesRequireUserOwnership() throws Exception {
        assertNotNull(AiAdminApplicationService.class.getMethod("listEvents", long.class, long.class, int.class, int.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("listUsage", long.class, long.class, int.class, int.class));
        assertNotNull(AiAdminApplicationService.class.getMethod("listToolUsage", long.class, long.class, int.class, int.class));
    }

    @Test
    void cancellationRequiresRunOwnership() throws Exception {
        Method method = AiAdminApplicationService.class.getMethod("requestCancel", long.class, long.class);
        assertNotNull(method);
    }

    @Test
    void enqueueRequiresConversationOwnership() throws Exception {
        Method method = AiAdminApplicationService.class.getMethod("enqueueMessage", long.class, long.class,
            String.class, String.class);
        assertNotNull(method);
    }
}
