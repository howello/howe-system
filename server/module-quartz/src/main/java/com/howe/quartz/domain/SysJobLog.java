package com.howe.quartz.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.common.annotation.Excel;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 定时任务调度日志表 sys_job_log
 *
 * @author howe
 */
@Schema(description = "定时任务调度日志")
public class SysJobLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    @Excel(name = "日志序号")
    @Schema(description = "任务日志ID", example = "1")
    private Long jobLogId;

    /** 任务名称 */
    @Excel(name = "任务名称")
    @Schema(description = "任务名称", example = "系统默认（无参）")
    private String jobName;

    /** 任务组名 */
    @Excel(name = "任务组名")
    @Schema(description = "任务组名", example = "DEFAULT")
    private String jobGroup;

    /** 调用目标字符串 */
    @Excel(name = "调用目标字符串")
    @Schema(description = "调用目标字符串", example = "ryTask.ryNoParams")
    private String invokeTarget;

    /** 日志信息 */
    @Excel(name = "日志信息")
    @Schema(description = "日志信息", example = "系统默认（无参） 总共耗时：3毫秒")
    private String jobMessage;

    /** 执行状态（0正常 1失败 2执行中） */
    @Excel(name = "执行状态", readConverterExp = "0=正常,1=失败,2=执行中")
    @Schema(description = "执行状态（0正常 1失败 2执行中）", example = "0")
    private String status;

    /** 异常信息 */
    @Excel(name = "异常信息")
    @Schema(description = "异常信息")
    private String exceptionInfo;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间", example = "2026-07-31 10:00:00")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间", example = "2026-07-31 10:00:03")
    private Date endTime;

    public Long getJobLogId()
    {
        return jobLogId;
    }

    public void setJobLogId(Long jobLogId)
    {
        this.jobLogId = jobLogId;
    }

    public String getJobName()
    {
        return jobName;
    }

    public void setJobName(String jobName)
    {
        this.jobName = jobName;
    }

    public String getJobGroup()
    {
        return jobGroup;
    }

    public void setJobGroup(String jobGroup)
    {
        this.jobGroup = jobGroup;
    }

    public String getInvokeTarget()
    {
        return invokeTarget;
    }

    public void setInvokeTarget(String invokeTarget)
    {
        this.invokeTarget = invokeTarget;
    }

    public String getJobMessage()
    {
        return jobMessage;
    }

    public void setJobMessage(String jobMessage)
    {
        this.jobMessage = jobMessage;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getExceptionInfo()
    {
        return exceptionInfo;
    }

    public void setExceptionInfo(String exceptionInfo)
    {
        this.exceptionInfo = exceptionInfo;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("jobLogId", getJobLogId())
            .append("jobName", getJobName())
            .append("jobGroup", getJobGroup())
            .append("jobMessage", getJobMessage())
            .append("status", getStatus())
            .append("exceptionInfo", getExceptionInfo())
            .append("startTime", getStartTime())
            .append("stopTime", getEndTime())
            .toString();
    }
}
