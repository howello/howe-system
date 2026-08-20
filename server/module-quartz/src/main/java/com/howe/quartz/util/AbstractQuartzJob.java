package com.howe.quartz.util;

import java.util.Date;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.howe.common.constant.Constants;
import com.howe.common.constant.ScheduleConstants;
import com.howe.common.task.TaskLogContext;
import com.howe.common.utils.ExceptionUtil;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.bean.BeanUtils;
import com.howe.common.utils.spring.SpringUtils;
import com.howe.quartz.domain.SysJob;
import com.howe.quartz.domain.SysJobLog;
import com.howe.quartz.service.ISysJobLogService;

/**
 * 抽象quartz调用。
 *
 * <p>调度日志在任务开始时先落库，任务结束时更新，这样任务线程可以拿到
 * {@code jobLogId} 写入步骤明细。</p>
 *
 * @author howe
 */
public abstract class AbstractQuartzJob implements Job
{
    private static final Logger log = LoggerFactory.getLogger(AbstractQuartzJob.class);

    /** 当前 Quartz 工作线程的执行状态。 */
    private static final ThreadLocal<ExecutionState> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public void execute(JobExecutionContext context)
    {
        SysJob sysJob = new SysJob();
        BeanUtils.copyBeanProp(sysJob, context.getMergedJobDataMap().get(ScheduleConstants.TASK_PROPERTIES));
        try
        {
            before(context, sysJob);
            doExecute(context, sysJob);
            after(context, sysJob, null);
        }
        catch (Exception e)
        {
            log.error("任务执行异常  - ：", e);
            after(context, sysJob, e);
        }
    }

    /**
     * 执行前先创建调度日志。
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     */
    protected void before(JobExecutionContext context, SysJob sysJob)
    {
        Date startTime = new Date();
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobName(sysJob.getJobName());
        jobLog.setJobGroup(sysJob.getJobGroup());
        jobLog.setInvokeTarget(sysJob.getInvokeTarget());
        jobLog.setJobMessage("任务开始执行");
        jobLog.setStatus(Constants.RUNNING);
        jobLog.setStartTime(startTime);

        SpringUtils.getBean(ISysJobLogService.class).addJobLog(jobLog);
        THREAD_LOCAL.set(new ExecutionState(startTime, jobLog.getJobLogId()));
        TaskLogContext.bind(jobLog.getJobLogId());
    }

    /**
     * 执行后更新调度日志并清理线程上下文。
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     * @param exception 执行异常
     */
    protected void after(JobExecutionContext context, SysJob sysJob, Exception exception)
    {
        ExecutionState state = THREAD_LOCAL.get();
        try
        {
            if (state == null || state.jobLogId() == null)
            {
                log.error("调度日志上下文缺失，无法更新任务日志：{}", sysJob == null ? null : sysJob.getInvokeTarget());
                return;
            }

            Date endTime = new Date();
            long runMs = endTime.getTime() - state.startTime().getTime();
            SysJobLog jobLog = new SysJobLog();
            jobLog.setJobLogId(state.jobLogId());
            jobLog.setJobMessage((sysJob == null ? "任务" : sysJob.getJobName()) + " 总共耗时：" + runMs + "毫秒");
            jobLog.setEndTime(endTime);
            if (exception != null)
            {
                jobLog.setStatus(Constants.FAIL);
                jobLog.setExceptionInfo(TaskLogContext.sanitize(
                        StringUtils.substring(ExceptionUtil.getExceptionMessage(exception), 0, 2000)));
            }
            else
            {
                jobLog.setStatus(Constants.SUCCESS);
                jobLog.setExceptionInfo("");
            }
            SpringUtils.getBean(ISysJobLogService.class).updateJobLog(jobLog);
        }
        finally
        {
            TaskLogContext.clear();
            THREAD_LOCAL.remove();
        }
    }

    /**
     * 执行方法，由子类重载。
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     * @throws Exception 执行过程中的异常
     */
    protected abstract void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception;

    private record ExecutionState(Date startTime, Long jobLogId)
    {
    }
}
