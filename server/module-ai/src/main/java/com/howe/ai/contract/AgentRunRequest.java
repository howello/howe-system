package com.howe.ai.contract;

import java.util.Map;

public record AgentRunRequest(String agentId, String input, Map<String, Object> metadata) {
    public AgentRunRequest {
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("Agent 标识不能为空");
        if (input == null || input.isBlank()) throw new IllegalArgumentException("输入不能为空");
        if (metadata == null) throw new IllegalArgumentException("元数据不能为空");
        metadata = Map.copyOf(metadata);
    }
}
