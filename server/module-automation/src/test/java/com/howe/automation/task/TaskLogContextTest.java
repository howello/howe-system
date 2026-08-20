package com.howe.automation.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.howe.common.task.TaskLogContext;
import com.howe.common.task.TaskLogContext.TaskStep;
import com.howe.common.task.TaskLogPublisher;
import com.howe.common.task.TaskLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TaskLogContextTest
{
    private final RecordingPublisher publisher = new RecordingPublisher();

    @AfterEach
    void clearContext()
    {
        TaskLogContext.clear();
    }

    @Test
    void shouldPublishSequentialStepLifecycle()
    {
        TaskLogContext.bind(42L, publisher);

        try (TaskStep step = TaskLogContext.startStep("打开页面"))
        {
            step.success("页面已打开");
        }
        try (TaskStep step = TaskLogContext.startStep("登录"))
        {
            step.needsAuth("需要登录");
        }

        assertEquals(2, publisher.started.size());
        assertEquals("RUNNING", publisher.started.get(1L).status());
        assertEquals("SUCCESS", publisher.finished.get(1L).status());
        assertEquals(1, publisher.finished.get(1L).stepNo());
        assertEquals("NEEDS_AUTH", publisher.finished.get(2L).status());
        assertEquals(2, publisher.finished.get(2L).stepNo());
    }

    @Test
    void shouldPublishInfoWithoutFinishingStep()
    {
        TaskLogContext.bind(42L, publisher);

        try (TaskStep step = TaskLogContext.startStep("同步文章"))
        {
            step.info("开始同步");
            step.skipped("没有需要同步的数据");
            assertEquals(0, publisher.finished.size());
        }

        assertEquals(2, publisher.infos.size());
        assertEquals("INFO", publisher.infos.get(0).status());
        assertEquals("SKIPPED", publisher.infos.get(1).status());
        assertEquals(1, publisher.finished.size());
        assertEquals("FAILED", publisher.finished.get(1L).status());
    }

    @Test
    void shouldFinishAfterMultipleInfoMessages()
    {
        TaskLogContext.bind(42L, publisher);

        try (TaskStep step = TaskLogContext.startStep("同步文章"))
        {
            step.info("开始同步");
            step.info("读取到 {} 篇文章", 3);
            step.success("同步完成");
        }

        assertEquals(2, publisher.infos.size());
        assertEquals(1, publisher.finished.size());
        assertEquals("SUCCESS", publisher.finished.get(1L).status());
    }

    @Test
    void shouldRedactCredentialFields()
    {
        assertEquals("password=[REDACTED] token=[REDACTED]", TaskLogContext.sanitize(
                "password=secret token=abc123"));
    }

    @Test
    void shouldMarkUnfinishedStepAsFailed()
    {
        TaskLogContext.bind(42L, publisher);

        try (TaskStep ignored = TaskLogContext.startStep("未完成步骤"))
        {
            // close() 应将未显式结束的步骤标记为失败。
        }

        assertNotNull(publisher.finished.get(1L));
        assertEquals("FAILED", publisher.finished.get(1L).status());
    }

    private static final class RecordingPublisher implements TaskLogPublisher
    {
        private final Map<Long, TaskLogRecord> started = new LinkedHashMap<>();
        private final Map<Long, TaskLogRecord> finished = new LinkedHashMap<>();
        private final List<TaskLogRecord> infos = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public Long begin(TaskLogRecord record)
        {
            long id = nextId++;
            started.put(id, record);
            return id;
        }

        @Override
        public void info(TaskLogRecord record)
        {
            infos.add(record);
        }

        @Override
        public void finish(Long detailId, TaskLogRecord record)
        {
            finished.put(detailId, record);
        }
    }
}
