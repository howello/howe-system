package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 待产包清单展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "待产包清单")
public class BagVO {

    @Schema(description = "清单ID")
    private String id;

    @Schema(description = "清单名称")
    private String name;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否默认清单")
    private Boolean defaultBag;

    @Schema(description = "物品")
    private List<BagItemVO> items;
}
