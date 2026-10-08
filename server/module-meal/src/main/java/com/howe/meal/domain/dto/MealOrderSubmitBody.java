package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 点餐端下单入参
 *
 * <p>购物车存在前端本地，下单时一次性提交，后端不保存购物车。
 * 菜名与封面由后端按 {@code dishId} 回查后写入明细快照，前端传的菜名一律忽略，
 * 避免前端伪造菜名污染订单。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "点餐端下单入参")
public class MealOrderSubmitBody implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "整体备注（口味要求等）", example = "爸爸不吃香菜")
    @Size(max = 500, message = "整体备注长度不能超过500个字符")
    private String orderRemark;

    @Schema(description = "点餐明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少点一道菜")
    @Valid
    private List<Item> items;

    /**
     * 单条点餐明细
     */
    @Data
    @Schema(description = "单条点餐明细")
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "菜品ID", example = "1")
        private Long dishId;

        @Schema(description = "份数", example = "1")
        private Integer count;

        @Schema(description = "单项备注（如不要辣）", example = "不要放太辣")
        @Size(max = 255, message = "单项备注长度不能超过255个字符")
        private String remark;
    }
}
