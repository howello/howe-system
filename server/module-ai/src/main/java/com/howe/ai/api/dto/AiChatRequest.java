package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * 文本对话请求
 *
 * <p>{@code messages} 与 {@code prompt} 二选一：{@code prompt} 是单轮简写，
 * 等价于追加一条 user 消息。除 {@code scene} 外的字段都可以留空，
 * 留空时按「全局默认 &lt; 场景 params &lt; 请求显式字段」的顺序合并。</p>
 *
 * <p>用 {@code @SuperBuilder} 而非 {@code @Builder}：{@link AiVisionRequest} 继承本类，
 * 需要把父类字段一起纳入 builder。</p>
 *
 * @author howe
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文本对话请求")
public class AiChatRequest
{
    @Schema(description = "场景标识，路由的 key", example = "blog.recipe.summary")
    private String scene;

    @Schema(description = "消息列表，与 prompt 二选一")
    private List<AiMessage> messages;

    @Schema(description = "单轮简写，等价于一条 user 消息", example = "用一句话介绍这道菜")
    private String prompt;

    @Schema(description = "系统提示词", example = "你是一位中餐菜谱编辑")
    private String system;

    @Schema(description = "指定模型名，留空走场景路由或全局默认")
    private String model;

    @Schema(description = "指定渠道 ID，留空走场景路由或全局默认")
    private String channel;

    @Schema(description = "采样温度")
    private Double temperature;

    @Schema(description = "最大输出 token 数")
    private Integer maxTokens;

    @Schema(description = "核采样")
    private Double topP;

    @Schema(description = "结构化输出用的 JSON Schema 字符串")
    private String outputSchema;

    @Schema(description = "超时（毫秒），留空取全局配置")
    private Long timeoutMs;

    @Schema(description = "业务附加信息，只用于调用记录，不参与模型调用")
    private Map<String, String> metadata;
}
