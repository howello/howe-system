package com.howe.ai.provider;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** AI 网关支持的固定服务商及其默认接入点。 */
@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AiProvider {
    DASHSCOPE("dashscope", "阿里百炼", "dashscope", "https://dashscope.aliyuncs.com"),
    OPENAI("openai-compatible", "OpenAI", "openai", "https://api.openai.com"),
    ANTHROPIC("anthropic", "Anthropic", "anthropic", "https://api.anthropic.com");

    private final String code;
    private final String name;
    private final String protocol;
    private final String defaultBaseUrl;

    /**
     * 按渠道存储的服务商标识查找固定服务商。
     *
     * @param code 服务商标识
     * @return 服务商，旧协议或未知标识返回 null
     */
    public static AiProvider fromCode(String code) {
        for (AiProvider provider : values()) {
            if (provider.code.equals(code)) {
                return provider;
            }
        }
        return null;
    }
}
