package com.howe.meal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 点餐-分类关联对象 meal_category_ref
 *
 * <p>菜品与提案共用一张多态关联表：{@code biz_type} 区分业务
 * （{@link #BIZ_DISH}=1 菜品、{@link #BIZ_PROPOSAL}=2 提案），
 * {@code biz_id} 为对应的 dish_id / proposal_id。</p>
 *
 * <p>分类是唯一主类：表主键为 {@code (biz_type, biz_id)}，一条业务最多一条分类关联。</p>
 *
 * <p>该类既作关联表的读写载体，也承载业务类型常量。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "点餐-分类关联（唯一主类）")
public class MealCategoryRef implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 业务类型：菜品 */
    public static final int BIZ_DISH = 1;
    /** 业务类型：提案 */
    public static final int BIZ_PROPOSAL = 2;

    @Schema(description = "业务类型（1=菜品 2=提案）", example = "1")
    private Integer bizType;

    @Schema(description = "业务ID（dish_id 或 proposal_id）", example = "1")
    private Long bizId;

    @Schema(description = "分类ID（唯一主类）", example = "1")
    private Long categoryId;
}
