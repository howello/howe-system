package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 待办展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "待办")
public class TodoVO {

    @Schema(description = "待办ID")
    private String id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "日期")
    private String dueDate;

    @Schema(description = "可选时刻")
    private String dueTime;

    @Schema(description = "是否每天重复提醒")
    private Boolean repeatDaily;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "是否完成")
    private Boolean done;

    @Schema(description = "完成时刻")
    private String doneAt;

    @Schema(description = "完成人账号ID")
    private String doneBy;

    @Schema(description = "版本号")
    private Integer version;
}
