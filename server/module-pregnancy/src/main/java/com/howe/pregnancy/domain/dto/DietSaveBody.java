package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 饮食记录保存请求
 *
 * @author howe
 */
@Data
@Schema(description = "饮食记录保存请求")
public class DietSaveBody {

    @Schema(description = "客户端请求ID，新增时必填")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "餐次（BREAKFAST/LUNCH/DINNER/SNACK）")
    @NotBlank(message = "餐次不能为空")
    private String mealType;

    @Schema(description = "发生时刻（带偏移 ISO 8601）")
    @NotBlank(message = "饮食时间不能为空")
    private String occurredAt;

    @Schema(description = "内容")
    @NotBlank(message = "饮食内容不能为空")
    @Size(max = 2000, message = "内容长度不能超过2000")
    private String content;

    @Schema(description = "备注")
    @Size(max = 2000, message = "备注长度不能超过2000")
    private String note;

    @Schema(description = "能量等补充文字")
    @Size(max = 1000, message = "补充文字长度不能超过1000")
    private String extraText;

    @Schema(description = "文件ID列表，可为空")
    private List<String> fileIds;

    @Schema(description = "版本号，编辑时必填")
    private Integer version;
}
