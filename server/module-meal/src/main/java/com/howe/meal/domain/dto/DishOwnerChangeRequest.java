package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 菜品归属调整请求
 *
 * <p>归属调整走独立接口而不是塞进普通修改：它改变的是菜谱可见范围，
 * 必须有独立确认与独立权限，避免一次普通编辑顺带把公共菜变成某家的私菜。</p>
 *
 * @param targetDeptId   目标归属：0 表示平台公共菜谱，否则为有效家庭 ID
 * @param expectedDeptId 客户端读到的旧归属，用于并发校验；与库中不一致时拒绝
 * @author howe
 */
@Schema(description = "菜品归属调整请求")
public record DishOwnerChangeRequest(
        @NotNull(message = "目标归属不能为空")
        @Schema(description = "目标归属：0 表示平台公共菜谱，否则为家庭 ID", example = "110")
        Long targetDeptId,
        @NotNull(message = "原归属不能为空")
        @Schema(description = "客户端读到的旧归属，用于并发校验", example = "0")
        Long expectedDeptId) {
}
