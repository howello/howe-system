package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 产检保存请求
 *
 * @author howe
 */
@Data
@Schema(description = "产检保存请求")
public class CheckupSaveBody {

    @Schema(description = "客户端请求ID，手动新增时必填")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "标题")
    @NotBlank(message = "产检标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    @Schema(description = "建议窗口起始孕日")
    private Integer suggestStartDay;

    @Schema(description = "建议窗口结束孕日")
    private Integer suggestEndDay;

    @Schema(description = "手填建议日期")
    private String suggestedDate;

    @Schema(description = "新增时同时预约的日期（yyyy-MM-dd），可为空")
    private String appointmentDate;

    @Schema(description = "医院")
    @Size(max = 200, message = "医院长度不能超过200")
    private String hospital;

    @Schema(description = "地点")
    @Size(max = 200, message = "地点长度不能超过200")
    private String location;

    @Schema(description = "本次重点说明")
    private String focus;

    @Schema(description = "版本号，编辑时必填")
    private Integer version;

    @Schema(description = "项目标题列表，原子替换")
    private List<DetailItem> items;

    @Schema(description = "准备事项标题列表，原子替换")
    private List<DetailItem> preparations;

    /**
     * 详情项
     */
    @Data
    @Schema(description = "产检详情项")
    public static class DetailItem {

        @Schema(description = "标题")
        @NotBlank(message = "详情标题不能为空")
        private String title;

        @Schema(description = "说明")
        private String description;

        @Schema(description = "项目预计耗时（分钟）")
        private Integer estimatedMinutes;
    }
}
