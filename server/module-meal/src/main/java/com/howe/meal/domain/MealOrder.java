package com.howe.meal.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * 点餐-订单对象 meal_order
 *
 * <p>订单是纯家庭私有数据，list 查询走 {@code @DataScope}；
 * 但「按 id 取详情」不受数据权限保护，必须在 Service 层比对 {@code deptId}。</p>
 *
 * <p>订单没有预约用餐时间，只有 {@code createTime}（下单时间）。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-订单")
public class MealOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 状态：待接单 */
    public static final String STATUS_WAITING = "0";
    /** 状态：制作中 */
    public static final String STATUS_COOKING = "1";
    /** 状态：已完成 */
    public static final String STATUS_FINISHED = "2";
    /** 状态：已取消 */
    public static final String STATUS_CANCELED = "3";
    /** 状态：无人接单（超时或全部候选人拒绝） */
    public static final String STATUS_UNACCEPTED = "4";

    /** 指派模式：公开抢单 */
    public static final String ASSIGN_MODE_PUBLIC = "0";
    /** 指派模式：指派中，只有被指派厨师可接单或拒绝 */
    public static final String ASSIGN_MODE_ASSIGNED = "1";
    /** 指派模式：指派被拒或超时后已转公开池 */
    public static final String ASSIGN_MODE_REOPENED = "2";

    /** 关闭原因：下单人取消 */
    public static final String CLOSE_REASON_CANCELED = "1";
    /** 关闭原因：超时无人接单 */
    public static final String CLOSE_REASON_TIMEOUT = "2";
    /** 关闭原因：全部候选人拒绝 */
    public static final String CLOSE_REASON_ALL_REFUSED = "3";

    @Schema(description = "订单ID", example = "1")
    private Long orderId;

    @Schema(description = "订单号", example = "M20260922001")
    private String orderNo;

    @Schema(description = "所属家庭ID", example = "100")
    private Long deptId;

    @Schema(description = "点餐人ID", example = "2")
    private Long userId;

    @Schema(description = "点餐人昵称", example = "妈妈")
    private String userName;

    @Schema(description = "状态（0待接单 1制作中 2已完成 3已取消）", example = "0")
    private String status;

    @Schema(description = "菜品总份数", example = "3")
    private Integer totalCount;

    @Schema(description = "整体备注（口味要求等）", example = "少放辣")
    private String orderRemark;

    @Schema(description = "接单人")
    private String acceptBy;
    /** 接单厨师用户ID，历史订单可能为空 */
    @JsonIgnore
    @Schema(description = "接单人用户ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long acceptUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "接单时间")
    private Date acceptTime;

    @Schema(description = "完成人", example = "爸爸")
    private String finishBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private Date finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "取消时间")
    private Date cancelTime;

    @Schema(description = "指派模式（0公开抢单 1指派中 2指派已转公开）", example = "0")
    private String assignMode;

    @JsonIgnore
    @Schema(description = "指派的厨师用户ID（下单时确定，不提供改派）", accessMode = Schema.AccessMode.READ_ONLY)
    private Long assignUserId;

    @Schema(description = "指派的厨师昵称（冗余快照）", example = "爸爸")
    private String assignUserName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "接单截止时间")
    private Date deadlineTime;

    @Schema(description = "关闭原因（1用户取消 2超时 3全员拒绝）", example = "2")
    private String closeReason;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;

    /** 点餐明细（查询结果附带，不落库） */
    @Schema(description = "点餐明细（查询结果附带，不落库）")
    private List<MealOrderItem> items;

    /** 已等待分钟数（后端计算，不落库） */
    @Schema(description = "已等待分钟数（后端计算，不落库）", example = "3")
    private Long waitMinutes;

    /** 已制作分钟数（后端计算，不落库） */
    @Schema(description = "已制作分钟数（后端计算，不落库）", example = "8")
    private Long cookMinutes;

    /** 查询用：状态集合（不落库） */
    @Schema(description = "状态集合（多状态查询用，不落库）")
    private List<String> statusList;

    /** 查询用：是否已评价（不落库），前端据此决定显示「去评价」还是「已评价」 */
    @Schema(description = "是否已评价（查询结果附带，不落库）")
    private Boolean reviewed;

    /** 状态流转用：期望的当前状态，作为 where 条件防止并发下重复流转（不落库） */
    @Schema(description = "期望的当前状态（状态流转的并发保护，不落库）")
    private String expectedStatus;

    /** 查询用：是否只保留当前用户仍是待响应候选人的订单（不落库） */
    @JsonIgnore
    @Schema(description = "候选人过滤用：当前登录用户ID（不落库）", accessMode = Schema.AccessMode.WRITE_ONLY)
    private Long candidateUserId;

    /** 状态流转用：是否要求订单已过接单截止时间（超时关闭用，不落库） */
    @JsonIgnore
    @Schema(description = "状态流转用：是否要求已过截止时间（不落库）", accessMode = Schema.AccessMode.WRITE_ONLY)
    private Boolean requireExpired;
}
