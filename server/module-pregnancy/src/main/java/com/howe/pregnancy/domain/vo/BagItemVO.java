package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 待产包物品展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "待产包物品")
public class BagItemVO {

    @Schema(description = "清单素材配图地址")
    private String imageUrl;

    @Schema(description = "物品ID")
    private String id;

    @Schema(description = "清单ID")
    private String bagId;

    @Schema(description = "分类")
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

    @Schema(description = "清单提供的物品介绍")
    private String introduction;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否已准备")
    private Boolean prepared;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "AI 介绍文本")
    private String aiText;

    @Schema(description = "AI 配图地址")
    private String aiImageUrl;

    @Schema(description = "内容生成状态")
    private String contentState;

    @Schema(description = "当前内容任务ID")
    private String contentTaskId;

    @Schema(description = "当前费用")
    private ExpenseVO expense;
}
