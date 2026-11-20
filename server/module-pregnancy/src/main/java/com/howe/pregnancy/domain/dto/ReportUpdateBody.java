package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 报告编辑请求
 *
 * <p>PATCH 语义：未出现的字段保持原值；显式传 {@code fileIds} 才原子替换图片关联。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "报告编辑请求")
public class ReportUpdateBody {

    @Schema(description = "版本号，必填")
    private Integer version;

    @Schema(description = "报告名称")
    private String name;

    @Schema(description = "报告日期")
    private String reportDate;

    @Schema(description = "关联产检ID，空字符串表示解除关联")
    private String checkupId;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "文件ID列表；省略不改")
    private List<String> fileIds;
}
