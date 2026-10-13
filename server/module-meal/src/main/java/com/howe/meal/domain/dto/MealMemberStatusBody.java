package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手机端家庭成员状态变更请求
 *
 * @author howe
 */
@Data
@Schema(description = "家庭成员状态变更请求")
public class MealMemberStatusBody {

    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @Schema(description = "账号状态（0正常 1停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private String status;
}
