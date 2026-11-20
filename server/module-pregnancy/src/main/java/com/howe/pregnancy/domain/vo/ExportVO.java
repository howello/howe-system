package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 导出任务展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "孕期导出任务")
public class ExportVO {

    @Schema(description = "任务ID")
    private String id;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "阶段")
    private String stage;

    @Schema(description = "快照时刻")
    private String snapshotAt;

    @Schema(description = "结果到期时刻")
    private String expiresAt;

    @Schema(description = "文件大小（字节）")
    private String fileSize;

    @Schema(description = "结果摘要")
    private String summary;

    @Schema(description = "错误提示")
    private String message;

    @Schema(description = "下载地址")
    private String contentUrl;
}
