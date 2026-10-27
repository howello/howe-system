package com.howe.meal.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

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

    /** 查询用：下单时间（不落库） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "订单下单时间（查询结果附带，不落库）")
    private Date orderCreateTime;

    /** 查询用：订单整体备注（不落库） */
    @Schema(description = "订单整体备注（查询结果附带，不落库）")
    private String orderRemark;

    /** 查询用：订单总份数（不落库） */
    @Schema(description = "订单总份数（查询结果附带，不落库）", example = "3")
    private Integer orderTotalCount;

    /** 查询用：订单状态（不落库），取值同 MealOrder.STATUS_* */
    @Schema(description = "订单状态（查询结果附带，不落库）", example = "2")
    private String orderStatus;

    /** 查询用：接单厨师昵称（不落库），即「谁做的这顿饭」 */
    @Schema(description = "接单厨师昵称（查询结果附带，不落库）", example = "爸爸")
    private String acceptBy;

    /** 查询用：接单时间（不落库） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "接单时间（查询结果附带，不落库）")
    private Date acceptTime;

    /** 查询用：完成时间（不落库） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间（查询结果附带，不落库）")
    private Date finishTime;

    /** 查询用：订单明细快照（不落库），用于展示菜品封面、份数与单项备注 */
    @Schema(description = "订单明细（查询结果附带，不落库）")
    private List<MealOrderItem> items;

    /**
     * 查询用：接单厨师用户ID（不落库）。
     *
     * <p>厨师端「收到的评价」按此过滤——评价挂在订单上，订单的 {@code accept_user_id}
     * 才是「谁做的这顿饭」。</p>
     */
    @JsonIgnore
    @Schema(description = "接单厨师用户ID（查询过滤用，不落库）", accessMode = Schema.AccessMode.READ_ONLY)
    private Long acceptUserId;
}
