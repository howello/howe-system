package com.howe.meal.service;

import cn.hutool.core.util.IdUtil;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 菜品 AI 更新任务的受理与结果查询，进程中断后通过超时明确标记失败。 */
@Slf4j
@Service
public class MealDishAiTaskService {
    private final RedisCache redis;
    private final Executor executor;
    private static final String PREFIX = "meal:ai-task:";

    /** 注入独立 AI 线程池，不允许退化到请求线程执行。 */
    public MealDishAiTaskService(RedisCache redis, @Qualifier("aiTaskExecutor") Executor executor) {
        this.redis = redis;
        this.executor = executor;
    }

    /** 任务公开状态，不保存用户的完整提示词。 */
    public record Status(String taskId, Long userId, Long deptId, Long dishId, String state,
            String message, long createdAt) {
    }

    /** 先持久化受理状态，再提交任务；池满或状态写入失败必须向调用方报错。 */
    public String submit(Long dishId, Supplier<Boolean> action) {
        Status queued = new Status(IdUtil.fastSimpleUUID(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(),
                dishId, "PENDING", "等待处理", System.currentTimeMillis());
        save(queued);
        try {
            executor.execute(() -> {
                try {
                    if (System.currentTimeMillis() - queued.createdAt() > TimeUnit.HOURS.toMillis(1)) {
                        fail(queued, "任务等待超时，未执行，请重新提交");
                        return;
                    }
                    save(state(queued, "RUNNING", "正在更新"));
                    boolean success = action.get();
                    save(state(queued, success ? "SUCCEEDED" : "FAILED",
                            success ? "菜品已更新" : "更新失败，原内容已保留，请重试"));
                } catch (Exception e) {
                    log.warn("菜品 AI 任务失败，taskId={}", queued.taskId(), e);
                    fail(queued, "任务结果未确认，请刷新菜品查看后重试");
                }
            });
        } catch (Exception e) {
            fail(queued, "任务未受理，请稍后重试");
            throw new ServiceException("AI 任务繁忙，未提交成功，请稍后重试");
        }
        return queued.taskId();
    }

    /** 仅允许任务本人或平台管理员查看，超时任务不会永久显示正在执行。 */
    public Status get(String taskId) {
        if (taskId == null || !taskId.matches("[a-f0-9]{32}")) throw new ServiceException("任务不存在");
        Status status = redis.getCacheObject(PREFIX + taskId);
        if (status == null) throw new ServiceException("任务不存在或结果已过期，请刷新菜品确认");
        if (!SecurityUtils.isAdmin() && (!status.userId().equals(SecurityUtils.getUserId())
                || !status.deptId().equals(SecurityUtils.getDeptId()))) throw new ServiceException("无权查看该任务");
        if (("PENDING".equals(status.state()) || "RUNNING".equals(status.state()))
                && System.currentTimeMillis() - status.createdAt() > TimeUnit.HOURS.toMillis(1)) {
            status = state(status, "FAILED", "任务超时或执行进程已中断，请刷新菜品确认后重试");
            save(status);
        }
        return status;
    }

    /** 保存一天的查询窗口，防止任务状态无限累积。 */
    private void save(Status status) {
        redis.setCacheObject(PREFIX + status.taskId(), status, 24, TimeUnit.HOURS);
    }

    /** 记录失败，结果存储故障只记录日志，不能覆盖原有提交错误。 */
    private void fail(Status status, String message) {
        try {
            save(state(status, "FAILED", message));
        } catch (Exception e) {
            log.warn("保存菜品 AI 失败状态异常，taskId={}", status.taskId(), e);
        }
    }

    /** 在保留任务归属与创建时间的前提下生成新的状态快照。 */
    private Status state(Status original, String state, String message) {
        return new Status(original.taskId(), original.userId(), original.deptId(), original.dishId(),
                state, message, original.createdAt());
    }
}
