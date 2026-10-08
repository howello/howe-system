package com.howe.meal.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 点餐-菜品对象 meal_dish
 *
 * <p>{@code deptId = 0} 表示平台预置的公共菜谱，所有家庭可见；具体家庭 ID 表示该家庭的私有菜。
 * 与分类同理，本表<b>不能走 {@code @DataScope}</b>，隔离条件在 MealDishMapper.xml 里手写。</p>
 *
 * <p>用料 / 做法 / 小贴士三列在库里是 json，Java 侧用字符串承载 JSON 文本，
 * 由前端负责组装与解析，避免为三列各引一个 TypeHandler。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-菜品")
public class MealDish extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "菜品ID", example = "1")
    private Long dishId;

    @Schema(description = "所属家庭ID（0=公共菜谱）", example = "0")
    private Long deptId;

    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "菜名", example = "红烧肉")
    @NotBlank(message = "菜名不能为空")
    @Size(max = 128, message = "菜名长度不能超过128个字符")
    private String name;

    @Schema(description = "封面图地址", example = "https://img.wyantao.com/dish/hongshaorou.jpg")
    @Size(max = 500, message = "封面图地址长度不能超过500个字符")
    private String cover;

    @Schema(description = "一句话简介", example = "肥而不腻，入口即化")
    @Size(max = 500, message = "简介长度不能超过500个字符")
    private String description;

    @Schema(description = "标签，逗号分隔", example = "家常,下饭")
    @Size(max = 500, message = "标签长度不能超过500个字符")
    private String tags;

    @Schema(description = "耗时", example = "90 分钟")
    @Size(max = 64, message = "耗时长度不能超过64个字符")
    private String duration;

    @Schema(description = "难度", example = "中等")
    @Size(max = 32, message = "难度长度不能超过32个字符")
    private String level;

    @Schema(description = "份量", example = "3 人份")
    @Size(max = 64, message = "份量长度不能超过64个字符")
    private String serve;

    @Schema(description = "热量", example = "520 千卡")
    @Size(max = 64, message = "热量长度不能超过64个字符")
    private String kcal;

    @Schema(description = "用料清单 JSON，形如 [{\"name\":\"五花肉\",\"amount\":\"600 g\"}]")
    private String ingredients;

    @Schema(description = "做法步骤 JSON，形如 [\"切块\",\"焯水\"]")
    private String steps;

    @Schema(description = "小贴士 JSON，形如 [\"小火慢炖\"]")
    private String tips;

    @Schema(description = "状态（0上架 1下架）", example = "0")
    private String status;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "来源（0自建 1新菜提案通过）", example = "0")
    private String source;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;

    /** 查询用：分类名称（不落库） */
    @Schema(description = "分类名称（查询结果附带，不落库）")
    private String categoryName;

    /** 查询用：关键词（菜名或食材，不落库） */
    @Schema(description = "关键词（按菜名或食材模糊匹配，不落库）")
    private String keyword;
}
