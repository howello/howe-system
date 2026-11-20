package com.howe.pregnancy.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 产检详情 preg_checkup_detail
 *
 * <p>项目与准备事项共用一张表，以 {@code type} 区分；准备事项的完成状态与操作人
 * 只在该类型下使用。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "产检详情")
public class PregCheckupDetail {

    private static final long serialVersionUID = 1L;

    @Schema(description = "详情ID")
    private Long id;

    @Schema(description = "产检ID")
    private Long checkupId;

    @Schema(description = "类型（ITEM/PREPARATION）")
    private String type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "项目预计耗时（分钟）")
    private Integer estimatedMinutes;

    @Schema(description = "准备事项是否完成")
    private Boolean done;

    @Schema(description = "完成时刻")
    private OffsetDateTime doneAt;

    @Schema(description = "完成人账号ID")
    private Long doneBy;
}
