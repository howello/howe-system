package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文生图请求
 *
 * <p>{@code prompt} 由业务模块自己拼好——AI 模块不认业务语义，
 * 只追加请求或场景中的风格描述。尺寸统一用 {@code 1024*1024} 形式，
 * 厂商侧的格式差异由模块内部转换。</p>
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文生图请求")
public class AiImageRequest
{
    @Schema(description = "场景标识，路由的 key", example = "blog.recipe.cover")
    private String scene;

    @Schema(description = "正向提示词，由业务模块拼好", example = "红烧肉，砂锅盛装")
    private String prompt;

    @Schema(description = "负向提示词")
    private String negativePrompt;

    @Schema(description = "尺寸，如 1024*1024，留空取场景或全局配置", example = "1024*1024")
    private String size;

    @Schema(description = "生成张数，默认 1，上限由 ai.image.maxCount 控制", example = "1")
    private Integer count;

    @Schema(description = "追加到提示词的风格描述，留空使用场景配置", example = "自然光美食摄影")
    private String styleSuffix;

    @Schema(description = "随机种子")
    private Long seed;

    @Schema(description = "指定模型名，留空时可通过场景或另一项指定目标")
    private String model;

    @Schema(description = "指定渠道 ID，留空时可通过场景或另一项指定目标")
    private String channel;

    @Schema(description = "超时（毫秒），留空取全局配置")
    private Long timeoutMs;

    @Schema(description = "业务附加信息，只用于调用记录，不参与模型调用")
    private Map<String, String> metadata;
}
