package com.howe.ai.contract;

import java.util.Map;

public record ModelRequest(String model, String prompt, Map<String, Object> parameters) {
    public ModelRequest {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("模型名称不能为空");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("提示词不能为空");
        if (parameters == null) throw new IllegalArgumentException("模型参数不能为空");
        parameters = Map.copyOf(parameters);
    }
}
