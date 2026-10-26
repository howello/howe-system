package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 可指派厨师的精简信息
 *
 * <p>确认下单页的「指派厨师」选择器只需要用户ID与展示名，不暴露手机号等其它成员字段。</p>
 *
 * @param userId   用户ID
 * @param nickName 展示名称（昵称，缺失时回退账号）
 * @author howe
 */
@Schema(description = "可指派厨师")
public record MealOrderAssignee(
        @Schema(description = "用户ID", example = "2") Long userId,
        @Schema(description = "展示名称", example = "爸爸") String nickName) {
}
