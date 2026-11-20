package com.howe.pregnancy.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.howe.pregnancy.util.MoneyTextSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用明细展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "孕期费用明细")
public class ExpenseVO {

    @Schema(description = "费用ID")
    private String id;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "来源业务ID")
    private String sourceId;

    @Schema(description = "来源槽位")
    private String sourceSlot;

    @Schema(description = "费用分类")
    private String categoryCode;

    @Schema(description = "费用名称")
    private String label;

    @Schema(description = "实付金额")
    @JsonSerialize(using = MoneyTextSerializer.class)
    private BigDecimal amount;

    @Schema(description = "实际支出日期")
    private String spentDate;

    @Schema(description = "操作人账号ID")
    private String actorUserId;

    @Schema(description = "版本号")
    private Integer version;
}
