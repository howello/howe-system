package com.howe.ai.contract;

public record ToolDefinition(String name, String description, String inputSchemaJson) {
    public ToolDefinition {
        requireText(name, "工具名称");
        requireText(description, "工具描述");
        requireText(inputSchemaJson, "工具输入 schema");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
    }
}
