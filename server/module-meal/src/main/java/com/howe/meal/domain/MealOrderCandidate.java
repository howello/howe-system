package com.howe.meal.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 点餐-订单候选厨师对象 meal_order_candidate
 *
 * <p>订单与「可以接这张单的厨师」之间的多对多关系。下单时按指派或公开模式生成候选人，
 * 接单、拒绝、超时与订单关闭都以这张表的状态为准：待接单列表只展示「我是待响应候选人」
 * 的订单，接单资格也只看这里。</p>
 *
 * <p>同一订单同一厨师只会有一行，由唯一键 {@code (order_id, user_id)} 保证；
 * 候选人只包含拥有 {@code meal_chef} 角色的家庭成员，家庭管理员不进入候选池。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "点餐-订单候选厨师")
public class MealOrderCandidate implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 候选人状态：待响应 */
    public static final String STATUS_PENDING = "0";
    /** 候选人状态：已接单 */
    public static final String STATUS_ACCEPTED = "1";
    /** 候选人状态：已拒绝 */
    public static final String STATUS_REFUSED = "2";
    /** 候选人状态：已失效（订单关闭或被他人接单） */
    public static final String STATUS_INVALID = "3";
    /** 候选人状态：已超时未响应（指派阶段截止仍未响应） */
    public static final String STATUS_EXPIRED = "4";

    /** 候选人来源：下单时指派 */
    public static final String SOURCE_ASSIGNED = "1";
    /** 候选人来源：下单时公开池 */
    public static final String SOURCE_PUBLIC = "2";
    /** 候选人来源：指派被拒或超时后补入公开池 */
    public static final String SOURCE_REOPENED = "3";

    @Schema(description = "候选人ID", example = "1")
    private Long candidateId;

    @Schema(description = "订单ID", example = "1")
    private Long orderId;

    @Schema(description = "所属家庭ID", example = "100")
    private Long deptId;

    @Schema(description = "候选厨师用户ID", example = "2")
    private Long userId;

    @Schema(description = "候选厨师昵称（冗余，免联表）", example = "爸爸")
    private String userName;

    @Schema(description = "来源（1指派 2公开池 3转公开补入）", example = "2")
    private String source;

    @Schema(description = "状态（0待响应 1已接单 2已拒绝 3已失效 4已超时未响应）", example = "0")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "响应截止时间")
    private Date expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "接单/拒绝/超时判定时间")
    private Date respondTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "入池时间")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;
}
