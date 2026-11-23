package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品 AI 更新请求
 *
 * <p>用户可提交新菜谱、修改要求或做法思路，后端参考已保存菜谱自动判断编辑范围。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜谱 AI 更新请求")
public class DishAiUpdateRequest {

    @Schema(description = "菜品ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long dishId;

    @Schema(description = "新菜谱、修改要求或做法思路", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "去掉洋葱，保留葱姜蒜，最后收汁时写清楚判断标准")
    private String text;
}
