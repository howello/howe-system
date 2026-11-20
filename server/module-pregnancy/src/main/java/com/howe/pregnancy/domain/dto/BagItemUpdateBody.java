package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 待产包物品编辑请求
 *
 * <p>PATCH 语义：未出现的字段保持原值；{@code expense} 省略表示不改金额，
 * 其 {@code amount} 为空表示清除该费用。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "待产包物品编辑请求")
public class BagItemUpdateBody {

    @Schema(description = "版本号，必填")
    private Integer version;

    @Schema(description = "分类（MOTHER/BABY）")
    private String category;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数量")
    private String quantity;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "用途")
    private String purpose;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "购买渠道")
    private String channel;

    @Schema(description = "购买日期（YYYY-MM-DD）")
    private String purchaseDate;

    @Schema(description = "是否已准备")
    private Boolean prepared;

    @Schema(description = "实付金额信息；省略不改")
    private BagItemSaveBody.ExpenseField expense;
}
