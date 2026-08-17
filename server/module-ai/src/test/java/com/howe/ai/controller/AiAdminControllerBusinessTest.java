package com.howe.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class AiAdminControllerBusinessTest {
    @Test
    void cancelRunIsMappedToHttpEndpoint() throws Exception {
        Method method = AiAdminController.class.getDeclaredMethod("cancel", long.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/runs/{runId}/cancel"}, mapping.value());
    }

    @Test
    void createConversationIsMappedAndProtected() throws Exception {
        // 创建会话：Chat 页发起对话的前置入口；必须鉴权、接收请求体、绑定当前登录用户。
        Method method = AiAdminController.class.getDeclaredMethod("createConversation",
            com.howe.ai.web.ConversationCreateRequest.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping, "createConversation 必须是 POST 端点");
        assertArrayEquals(new String[]{"/conversations"}, mapping.value());
        assertNotNull(method.getAnnotation(PreAuthorize.class),
            "创建会话必须受权限保护，禁止匿名发起对话");
        assertNotNull(method.getParameters()[0].getAnnotation(RequestBody.class),
            "创建会话必须通过请求体接收 Agent 归属，不得走 query 参数");
        assertNotNull(method.getAnnotation(Operation.class), "createConversation 必须补 Swagger @Operation");
    }

    @Test
    void listConversationsIsMappedAndProtected() throws Exception {
        // 会话列表：Chat 页展示当前用户的历史会话；必须按当前登录用户过滤，禁止越权枚举。
        Method method = AiAdminController.class.getDeclaredMethod("listConversations",
            String.class, int.class, int.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping, "listConversations 必须是 GET 端点");
        assertArrayEquals(new String[]{"/conversations"}, mapping.value());
        assertNotNull(method.getAnnotation(PreAuthorize.class),
            "会话列表必须受权限保护，禁止匿名枚举");
    }
}
