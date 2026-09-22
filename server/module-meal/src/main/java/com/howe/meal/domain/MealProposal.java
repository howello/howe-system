package com.howe.meal.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 点餐-新菜提案对象 meal_proposal
 *
 * <p>家庭成员提交想吃的新菜，管理员审核。通过时自动往 {@code meal_dish} 落一条菜品
 * （{@code source = 1}）并把生成的 {@code dishId} 回写本表。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-新菜提案")
public class MealProposal extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 状态：待审核 */
    public static final String STATUS_PENDING = "0";
    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "1";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "2";

    @Schema(description = "提案ID", example = "1")
    private Long proposalId;

    @Schema(description = "所属家庭ID", example = "100")
    private Long deptId;

    @Schema(description = "提交人ID", example = "2")
    private Long userId;

    @Schema(description = "提交人昵称", example = "妹妹")
    private String userName;

    @Schema(description = "菜名", example = "水煮牛肉")
    @NotBlank(message = "菜名不能为空")
    @Size(max = 128, message = "菜名长度不能超过128个字符")
    private String name;

    @Schema(description = "期望分类", example = "1")
    private Long categoryId;

    @Schema(description = "菜品介绍", example = "麻辣鲜香，牛肉滑嫩")
    @Size(max = 500, message = "菜品介绍长度不能超过500个字符")
    private String description;

    @Schema(description = "参考图")
    @Size(max = 500, message = "参考图地址长度不能超过500个字符")
    private String image;

    @Schema(description = "想吃的理由", example = "这周想吃点辣的")
    @Size(max = 500, message = "理由长度不能超过500个字符")
    private String reason;

    @Schema(description = "状态（0待审核 1已通过 2已驳回）", example = "0")
    private String status;

    @Schema(description = "审核人", example = "妈妈")
    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "审核时间")
    private Date auditTime;

    @Schema(description = "审核意见", example = "已上架，周末做给你们吃")
    private String auditRemark;

    @Schema(description = "通过后生成的菜品ID", example = "12")
    private Long dishId;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;

    /** 查询用：分类名称（不落库） */
    @Schema(description = "期望分类名称（查询结果附带，不落库）")
    private String categoryName;
}
