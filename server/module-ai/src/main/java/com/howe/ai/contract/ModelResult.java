package com.howe.ai.contract;

public record ModelResult(String content, ProviderErrorCategory errorCategory, String providerRequestId) {
    public ModelResult {
        if (errorCategory == null && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("成功结果必须包含内容");
        }
        if (errorCategory != null && content != null && !content.isBlank()) {
            throw new IllegalArgumentException("失败结果不能包含模型内容");
        }
    }

    public static ModelResult success(String content, String requestId) {
        return new ModelResult(content, null, requestId);
    }
}
