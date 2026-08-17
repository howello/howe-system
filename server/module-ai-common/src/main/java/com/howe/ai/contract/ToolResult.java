package com.howe.ai.contract;

public record ToolResult(boolean success, String content, String errorMessage) {
    public ToolResult {
        if (success && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("成功结果必须包含内容");
        }
        if (!success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("失败结果必须包含错误信息");
        }
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content, null);
    }

    public static ToolResult failure(String errorCode, String errorMessage) {
        if (errorCode == null || errorCode.isBlank()) throw new IllegalArgumentException("错误码不能为空");
        if (errorMessage == null || errorMessage.isBlank()) throw new IllegalArgumentException("错误信息不能为空");
        return new ToolResult(false, null, errorCode + ": " + errorMessage);
    }
}
