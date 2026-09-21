package com.howe.ai.persistence.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 场景路由 ai_scene_route
 *
 * <p>场景是业务方唯一需要约定的东西：业务传 {@code scene}，路由决定用哪个模型。
 * 换厂商只在后台改这一行，不重启、不改代码。</p>
 *
 * <p>字段名用 {@code routeParams} 而非 {@code params}：{@code BaseEntity} 已有一个
 * 用于承载查询扩展条件的 {@code params}，同名会让 Jackson 出现两个同名属性。
 * 库里的列名仍是 {@code params}。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 场景路由")
public class AiSceneRoute extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @NotBlank(message = "场景标识不能为空")
    @Size(max = 128, message = "场景标识长度不能超过128个字符")
    @Schema(description = "场景标识", example = "blog.recipe.cover")
    private String scene;

    @NotBlank(message = "能力类型不能为空")
    @Schema(description = "能力类型：CHAT / VISION / IMAGE / EMBEDDING", example = "IMAGE")
    private String capability;

    @NotNull(message = "主模型不能为空")
    @Schema(description = "主模型 ID")
    private Long primaryModelId;

    @Schema(description = "有序降级链（JSON 数组，元素为模型 ID）", example = "[3,7]")
    private String fallbackModelIds;

    @Schema(description = "场景级参数覆盖（JSON 对象）",
            example = "{\"temperature\":0.7,\"size\":\"1024*1024\",\"styleSuffixKey\":\"recipe.photo\"}")
    private String routeParams;

    @Schema(description = "是否启用（0停用 1启用）", example = "1")
    private Integer enabled;

    /** 主模型展示信息，查询时联表带出，不落库 */
    @Schema(description = "主模型名，仅查询时带出")
    private String primaryModelName;

    /** 主模型展示名，查询时联表带出，不落库 */
    @Schema(description = "主模型展示名，仅查询时带出")
    private String primaryModelDisplayName;
}
