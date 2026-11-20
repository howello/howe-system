package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 待产包物品 preg_bag_item
 *
 * <p>实付金额不存在本行，统一保存在 {@link PregExpense}，以来源类型
 * {@code BAG_ITEM} 与槽位 {@code TOTAL} 关联；切换准备状态不改变费用。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "待产包物品")
public class PregBagItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "物品ID")
    private Long id;

    @Schema(description = "清单ID")
    private Long bagId;

    @Schema(description = "清单内稳定物品键")
    private String seedKey;

    @Schema(description = "分类（MOTHER/BABY）")
    private String category;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数量")
    private String quantity;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "用途/使用说明")
    private String purpose;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "购买渠道")
    private String channel;

    /** 购买日期 */
    private LocalDate purchaseDate;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否已准备")
    private Boolean prepared;

    @Schema(description = "操作人账号ID")
    private Long actorUserId;

    @Schema(description = "已显示的 AI 介绍")
    private String aiText;

    @Schema(description = "已显示的 AI 配图键")
    private String aiImageKey;

    @Schema(description = "当前内容生成任务ID")
    private Long currentContentTaskId;

    @Schema(description = "客户端请求ID")
    private String requestId;

    @Schema(description = "请求内容摘要")
    private String requestHash;

    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "删除标记（0存在 2删除）")
    private String delFlag;


    @Schema(description = "当前费用（查询填充）")
    private PregExpense expense;

    @Schema(description = "内容生成状态（查询填充）")
    private String contentState;
}
