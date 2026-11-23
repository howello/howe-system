package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 制作中订单的新菜品明细；未提交的原明细视为取消。 */
@Schema(description = "制作中订单修改入参")
public record MealCookingOrderEditBody(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotEmpty(message = "请至少保留一道菜，或取消整单")
        @Size(max = 100, message = "每单最多100道菜")
        List<@NotNull @Valid Item> items) {

    /** 指定菜品和份数，明细 ID 为空时添加新菜。 */
    public record Item(
            @Schema(description = "原菜品明细 ID，添加菜品时省略")
            @Positive Long itemId,
            @NotNull @Positive Long dishId,
            @NotNull @Min(1) @Max(99) Integer count) {
    }
}
