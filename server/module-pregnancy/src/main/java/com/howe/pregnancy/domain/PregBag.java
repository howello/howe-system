package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 待产包清单 preg_bag
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "待产包清单")
public class PregBag extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "清单ID")
    private Long id;

    @Schema(description = "默认清单稳定键")
    private String seedKey;

    @Schema(description = "清单名称")
    private String name;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否为默认清单")
    private Boolean defaultBag;

    @Schema(description = "物品（按需填充）")
    private List<PregBagItem> items;
}
