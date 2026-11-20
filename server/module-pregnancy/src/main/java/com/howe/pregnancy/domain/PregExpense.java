package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 统一费用明细 preg_expense
 *
 * <p>所有金额只保存在这里：产检、报告和待产包物品通过来源关系关联费用。
 * {@code source_slot} 区分同一来源下的多条费用；待产包固定使用 {@code TOTAL}。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "孕期费用明细")
public class PregExpense extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "费用ID")
    private Long id;

    @Schema(description = "来源类型（CHECKUP/REPORT/BAG_ITEM）")
    private String sourceType;

    @Schema(description = "来源业务ID")
    private Long sourceId;

    @Schema(description = "来源槽位")
    private String sourceSlot;

    @Schema(description = "费用分类")
    private String categoryCode;

    @Schema(description = "费用名称")
    private String label;

    @Schema(description = "实付金额")
    private BigDecimal amount;

    @Schema(description = "实际支出日期")
    private LocalDate spentDate;

    @Schema(description = "操作人账号ID")
    private Long actorUserId;

    @Schema(description = "客户端请求ID")
    private String requestId;

    @Schema(description = "请求内容摘要")
    private String requestHash;

    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "删除标记（0存在 2删除）")
    private String delFlag;

}
