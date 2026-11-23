package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 待办编辑请求
 *
 * <p>字段为空表示保持原值；{@code dueTime} 需要清空时由显式空字符串表达。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "待办编辑请求")
public class TodoUpdateBody {

    @Schema(description = "版本号")
    @NotNull(message = "版本号不能为空")
    private Integer version;

    @Schema(description = "标题")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    @Schema(description = "日期")
    private String dueDate;

    @Schema(description = "可选时刻（HH:mm）")
    private String dueTime;

    @Schema(description = "是否每天重复提醒，为空时保持原值")
    private Boolean repeatDaily;

    @Schema(description = "备注")
    @Size(max = 2000, message = "备注长度不能超过2000")
    private String note;

    @Schema(description = "是否完成")
    private Boolean done;
}
