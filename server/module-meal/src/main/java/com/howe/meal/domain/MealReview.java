package com.howe.meal.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 点餐-评价对象 meal_review
 *
 * <p>评价粒度是<b>一单一评</b>：{@code order_id} 上有唯一约束
 * {@code uk_meal_review_order}，同一订单重复提交由数据库兜底、Service 层提前拦截。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-评价")
public class MealReview extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "评价ID", example = "1")
    private Long reviewId;

    @Schema(description = "订单ID", example = "1")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "所属家庭ID", example = "100")
    private Long deptId;

    @Schema(description = "评价人ID", example = "2")
    private Long userId;

    @Schema(description = "评价人昵称", example = "妈妈")
    private String userName;

    @Schema(description = "总体评分（1-5）", example = "5")
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer score;

    @Schema(description = "评价内容", example = "炒饭粒粒分明，虾仁很新鲜")
    @Size(max = 1000, message = "评价内容长度不能超过1000个字符")
    private String content;

    @Schema(description = "图片地址列表 JSON，形如 [\"https://...\"]")
    private String images;

    @Schema(description = "是否匿名（0否 1是）", example = "0")
    private String anonymous;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;

    /** 查询用：订单号（不落库） */
    @Schema(description = "订单号（查询结果附带，不落库）")
    private String orderNo;

    /** 查询用：该订单的菜名，顿号分隔（不落库） */
    @Schema(description = "订单菜名，顿号分隔（查询结果附带，不落库）", example = "红烧肉、扬州炒饭")
    private String dishNames;
}
