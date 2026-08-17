package com.howe.ai.contract;

/** 模型流式输出的一段文本。 */
public record ModelDelta(String content, int index) {
    public ModelDelta {
        if (content == null || content.isEmpty()) throw new IllegalArgumentException("模型增量不能为空");
        if (index < 0) throw new IllegalArgumentException("模型增量序号不能为负数");
    }
}
