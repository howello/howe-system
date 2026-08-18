package com.howe.common.task;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.howe.common.utils.ExceptionUtil;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.spring.SpringUtils;

/**
 * 当前 Quartz 执行线程的步骤日志上下文。
 *
 * <p>没有 Quartz 上下文时，步骤对象退化为空操作，便于任务被直接调用时做本地验证。</p>
 */
public final class TaskLogContext
{
    private static final Logger log = LoggerFactory.getLogger(TaskLogContext.class);

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private TaskLogContext()
    {
    }

    /**
     * 绑定一次调度执行。
     *
     * @param jobLogId 调度日志编号
     */
    public static void bind(Long jobLogId)
    {
        bind(jobLogId, null);
    }

    /**
     * 绑定一次调度执行，并显式指定发布器，供测试或嵌入式执行器使用。
     *
     * @param jobLogId 调度日志编号
     * @param publisher 步骤发布器，为 null 时从 Spring 容器获取
     */
    public static void bind(Long jobLogId, TaskLogPublisher publisher)
    {
        HOLDER.set(new Context(jobLogId, publisher));
    }

    /**
     * 清除当前线程上下文。Quartz 工作线程会复用，必须在 finally 中调用。
     */
    public static void clear()
    {
        HOLDER.remove();
    }

    /**
     * 脱敏日志文本中的常见凭据字段。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public static String sanitize(String text)
    {
        if (text == null)
        {
            return null;
        }
        return text.replaceAll(
                "(?i)((?:password|passwd|token|secret|cookie|authorization)\\s*[=:]\\s*)([^,\\s;&]+)",
                "$1[REDACTED]");
    }

    /**
     * 开始一个关键业务步骤。
     *
     * @param stepName 步骤名称
     * @return 步骤句柄
     */
    public static TaskStep startStep(String stepName)
    {
        Context context = HOLDER.get();
        TaskLogPublisher publisher = context == null || context.publisher == null
                ? getPublisher() : context.publisher;
        if (context == null || publisher == null)
        {
            return TaskStep.noop();
        }

        Date startTime = new Date();
        int stepNo = context.sequence.incrementAndGet();
        TaskLogRecord record = new TaskLogRecord(
                context.jobLogId,
                stepNo,
                stepName,
                TaskStepStatus.RUNNING,
                null,
                null,
                startTime,
                null,
                null);
        Long detailId = publisher.begin(record);
        return new TaskStep(publisher, detailId, context.jobLogId, stepNo, stepName, startTime);
    }

    private static TaskLogPublisher getPublisher()
    {
        try
        {
            return SpringUtils.getBean(TaskLogPublisher.class);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String exceptionMessage(Throwable throwable)
    {
        if (throwable == null)
        {
            return null;
        }
        String message = sanitize(ExceptionUtil.getExceptionMessage(throwable));
        return StringUtils.substring(message, 0, 2000);
    }

    private static final class Context
    {
        private final Long jobLogId;
        private final TaskLogPublisher publisher;
        private final AtomicInteger sequence = new AtomicInteger();

        private Context(Long jobLogId, TaskLogPublisher publisher)
        {
            this.jobLogId = jobLogId;
            this.publisher = publisher;
        }
    }

    /**
     * 单个步骤的生命周期句柄。
     */
    public static final class TaskStep implements AutoCloseable
    {
        private static final TaskStep NOOP = new TaskStep();

        private final TaskLogPublisher publisher;
        private final Long detailId;
        private final Long jobLogId;
        private final Integer stepNo;
        private final String stepName;
        private final Date startTime;
        private boolean finished;

        private TaskStep()
        {
            this.publisher = null;
            this.detailId = null;
            this.jobLogId = null;
            this.stepNo = null;
            this.stepName = null;
            this.startTime = null;
            this.finished = true;
        }

        private TaskStep(TaskLogPublisher publisher, Long detailId, Long jobLogId, Integer stepNo,
                String stepName, Date startTime)
        {
            this.publisher = publisher;
            this.detailId = detailId;
            this.jobLogId = jobLogId;
            this.stepNo = stepNo;
            this.stepName = stepName;
            this.startTime = startTime;
        }

        private static TaskStep noop()
        {
            return NOOP;
        }

        /**
         * 标记步骤成功。
         *
         * @param message 脱敏后的步骤消息
         */
        public void success(String message)
        {
            finish(TaskStepStatus.SUCCESS, message, null);
        }

        /**
         * 标记步骤跳过。
         *
         * @param message 跳过原因
         */
        public void skipped(String message)
        {
            finish(TaskStepStatus.SKIPPED, message, null);
        }

        /**
         * 标记需要人工补充登录配置。
         *
         * @param message 脱敏后的说明
         */
        public void needsAuth(String message)
        {
            finish(TaskStepStatus.NEEDS_AUTH, message, null);
        }

        /**
         * 标记步骤失败。
         *
         * @param message 脱敏后的失败说明
         * @param throwable 异常
         */
        public void fail(String message, Throwable throwable)
        {
            finish(TaskStepStatus.FAILED, message, exceptionMessage(throwable));
        }

        private void finish(String status, String message, String errorInfo)
        {
            if (finished || publisher == null)
            {
                return;
            }
            finished = true;
            Date endTime = new Date();
            TaskLogRecord record = new TaskLogRecord(
                    jobLogId,
                    stepNo,
                    stepName,
                    status,
                    StringUtils.substring(sanitize(message), 0, 2000),
                    errorInfo,
                    startTime,
                    endTime,
                    endTime.getTime() - startTime.getTime());
            publisher.finish(detailId, record);
        }

        /**
         * 未显式结束的步骤视为失败，避免异常中断后一直停留在 RUNNING。
         */
        @Override
        public void close()
        {
            if (!finished && publisher != null)
            {
                log.warn("任务步骤未显式结束：{}", stepName);
                fail("步骤未显式结束", null);
            }
        }
    }

    /** 步骤状态常量。 */
    public static final class TaskStepStatus
    {
        public static final String RUNNING = "RUNNING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String SKIPPED = "SKIPPED";
        public static final String NEEDS_AUTH = "NEEDS_AUTH";

        private TaskStepStatus()
        {
        }
    }
}
