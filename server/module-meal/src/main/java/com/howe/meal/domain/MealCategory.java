package com.howe.meal.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 点餐-菜品分类对象 meal_category
 *
 * <p>分类是<b>唯一主类</b>：固定 9 条平台公共分类（{@code deptId = 0}），名称由
 * {@link com.howe.meal.constant.MealCategoryTaxonomy} 定义，家庭不能自行新增；
 * 一道菜最多绑定其中一个。因此本表<b>不能走 {@code @DataScope}</b>
 * （数据权限只会拼出「本部门」，会把公共分类一起过滤掉），隔离条件在
 * MealCategoryMapper.xml 里手写。</p>
 *
 * <p>「招牌」不是分类：它只做点餐端推荐位，不落库，也不参与唯一主类判定。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-菜品分类（唯一主类）")
public class MealCategory extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "所属家庭ID（0=公共分类）", example = "0")
    private Long deptId;

    @Schema(description = "分类名称（9 个唯一主类之一）", example = "荤菜")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称长度不能超过64个字符")
    private String name;

    @Schema(description = "分类说明（一句话定位）", example = "以肉为主、肉量明显的菜")
    @Size(max = 255, message = "分类说明长度不能超过255个字符")
    private String description;

    @Schema(description = "分类依据（放什么、不放什么、示例）",
            example = "肉少菜多的不放；鱼虾蟹贝不放。示例：红烧肉、回锅肉")
    @Size(max = 1000, message = "分类依据长度不能超过1000个字符")
    private String basis;

    @Schema(description = "分类图标", example = "🍖")
    @Size(max = 500, message = "分类图标长度不能超过500个字符")
    private String icon;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;
}
