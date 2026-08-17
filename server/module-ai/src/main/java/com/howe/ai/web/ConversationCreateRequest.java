package com.howe.ai.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建会话请求体：Chat 页发起对话时绑定一个已发布的 Agent 作为会话主体。
 *
 * <p>会话归属当前登录用户，由 Controller 从 SecurityContext 注入，不信任请求体传入的 userId，
 * 因此本请求体只携带 Agent 编码与可选标题。</p>
 */
public record ConversationCreateRequest(
        @Schema(description = "Agent 编码", requiredMode = Schema.RequiredMode.REQUIRED) String agentKey,
        @Schema(description = "会话标题，可空，用于历史列表展示") String title) {
}
