package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 待办 preg_todo
 *
 * <p>允许未来日期，也允许提前勾选完成；完成后仍参与日历展示。
 * 待办不算实际记录，不参与「当天是否已有记录」的判定。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "孕期待办")
public class PregTodo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "待办ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "日期")
    private LocalDate dueDate;

    @Schema(description = "可选时刻")
    private LocalTime dueTime;

    @Schema(description = "是否每天重复提醒")
    private Boolean repeatDaily;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "是否完成")
    private Boolean done;

    @Schema(description = "完成时刻")
    private OffsetDateTime doneAt;

    @Schema(description = "完成人账号ID")
    private Long doneBy;

    @Schema(description = "客户端请求ID")
    private String requestId;

    @Schema(description = "请求内容摘要")
    private String requestHash;

    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "删除标记（0存在 2删除）")
    private String delFlag;

}
