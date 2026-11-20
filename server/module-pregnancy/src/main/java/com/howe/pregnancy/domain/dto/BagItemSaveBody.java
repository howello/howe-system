package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 待产包物品保存请求
 *
 * @author howe
 */
@Data
@Schema(description = "待产包物品保存请求")
public class BagItemSaveBody {

    @Schema(description = "客户端请求ID，新增时必填")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "清单ID，新增时必填")
    private String bagId;

    @Schema(description = "分类（MOTHER/BABY）")
    @NotBlank(message = "分类不能为空")
    private String category;

    @Schema(description = "名称")
    @NotBlank(message = "物品名称不能为空")
    @Size(max = 200, message = "名称长度不能超过200")
    private String name;

    @Schema(description = "数量")
    @Size(max = 64, message = "数量长度不能超过64")
    private String quantity;

    @Schema(description = "单位")
    @Size(max = 32, message = "单位长度不能超过32")
    private String unit;

    @Schema(description = "用途/使用说明")
    @Size(max = 1000, message = "用途长度不能超过1000")
    private String purpose;

    @Schema(description = "品牌")
    @Size(max = 200, message = "品牌长度不能超过200")
    private String brand;

    @Schema(description = "购买渠道")
    @Size(max = 200, message = "渠道长度不能超过200")
    private String channel;

    @Schema(description = "购买日期（YYYY-MM-DD）")
    private String purchaseDate;

    @Schema(description = "是否已准备")
    private Boolean prepared;

    @Schema(description = "版本号，编辑时必填")
    private Integer version;

    @Schema(description = "实付金额信息；省略不改，null 清除")
    private ExpenseField expense;

    /**
     * 实付金额字段
     */
    @Data
    @Schema(description = "待产包实付金额")
    public static class ExpenseField {

        @Schema(description = "实付金额，以字符串传递")
        private String amount;

        @Schema(description = "实际支出日期")
        private String spentDate;

        @Schema(description = "费用分类")
        private String category;

        @Schema(description = "费用名称")
        private String label;
    }
}
