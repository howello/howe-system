package com.howe.meal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 点餐-订单明细对象 meal_order_item
 *
 * <p>明细存的是<b>菜品快照</b>：菜名与封面在下单时复制一份，
 * 此后菜品改名、下架或删除都不会影响历史订单的展示。</p>
 *
 * <p>明细只随主表读写，没有独立的 Controller 与 Service。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "点餐-订单明细")
public class MealOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "明细ID", example = "1")
    private Long itemId;

    @Schema(description = "订单ID", example = "1")
    private Long orderId;

    @Schema(description = "菜品ID", example = "1")
    private Long dishId;

    @Schema(description = "菜名快照", example = "红烧肉")
    private String dishName;

    @Schema(description = "封面快照")
    private String dishCover;

    @Schema(description = "份数", example = "1")
    private Integer count;

    @Schema(description = "单项备注（如不要辣）", example = "不要放太辣")
    private String remark;
}
