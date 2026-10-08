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
 * <p>{@code deptId = 0} 表示平台预置的公共分类，所有家庭可见；
 * 具体家庭 ID 表示该家庭的私有分类。因此本表<b>不能走 {@code @DataScope}</b>
 * （数据权限只会拼出「本部门」，会把公共分类一起过滤掉），隔离条件在
 * MealCategoryMapper.xml 里手写。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-菜品分类")
public class MealCategory extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "所属家庭ID（0=公共分类）", example = "0")
    private Long deptId;

    @Schema(description = "分类名称", example = "热菜")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称长度不能超过64个字符")
    private String name;

    @Schema(description = "分类图标", example = "https://img.wyantao.com/icon/hot.png")
    @Size(max = 500, message = "分类图标长度不能超过500个字符")
    private String icon;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;
}
