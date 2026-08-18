package com.howe.ai.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record AgentDraftRequest(
        @Schema(description = "Agent 编码", requiredMode = Schema.RequiredMode.REQUIRED) String agentKey,
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "系统提示词", requiredMode = Schema.RequiredMode.REQUIRED) String systemPrompt,
        @Schema(description = "路由快照 JSON") String routeJson,
        @Schema(description = "Tool 白名单 JSON") String toolJson,
        @Schema(description = "预算 JSON") String budgetJson) {
    public String toJson() {
        return "{\"agentKey\":\"" + agentKey + "\",\"name\":\"" + name + "\",\"systemPrompt\":\"" + systemPrompt + "\",\"routeJson\":\"" + routeJson + "\",\"toolJson\":\"" + toolJson + "\",\"budgetJson\":\"" + budgetJson + "\"}";
    }
}
