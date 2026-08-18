package com.howe.ai.contract;

public record ToolRequest(String toolName, String argumentsJson) {
    public ToolRequest {
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("工具名称不能为空");
        if (argumentsJson == null || argumentsJson.isBlank()) throw new IllegalArgumentException("工具参数不能为空");
    }
}
