package com.howe.quartz.domain;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 定时任务步骤明细 sys_job_log_detail。
 */
@Data
@Schema(description = "定时任务步骤明细")
public class SysJobLogDetail
{
    @Schema(description = "步骤明细编号", example = "1")
    private Long detailId;

    @Schema(description = "调度日志编号", example = "1")
    private Long jobLogId;

    @Schema(description = "步骤序号", example = "1")
    private Integer stepNo;

    @Schema(description = "步骤名称", example = "打开登录页")
    private String stepName;

    @Schema(description = "步骤状态", example = "RUNNING")
    private String status;

    @Schema(description = "步骤消息")
    private String message;

    @Schema(description = "异常信息")
    private String errorInfo;

    @Schema(description = "开始时间")
    private Date startTime;

    @Schema(description = "结束时间")
    private Date endTime;

    @Schema(description = "耗时毫秒", example = "1250")
    private Long durationMs;

    @Schema(description = "创建时间")
    private Date createTime;
}
