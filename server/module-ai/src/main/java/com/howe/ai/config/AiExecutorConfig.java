package com.howe.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.concurrent.ThreadPoolExecutor;

/** 隔离 AI 长任务与调用日志，池满时显式拒绝，避免回到请求线程执行。 */
@Slf4j
@Configuration
public class AiExecutorConfig {
    /** 长任务池容量为启动期设置，默认最多四个任务并行。 */
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor(@Value("${howe.ai.executor.max:4}") int max,
            @Value("${howe.ai.executor.queue:16}") int queue) {
        ThreadPoolTaskExecutor pool = pool("ai-task-", Math.max(1, Math.min(max, 16)), Math.max(1, Math.min(queue, 200)));
        pool.setTaskDecorator(task -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return () -> {
                SecurityContext previous = SecurityContextHolder.getContext();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                try {
                    SecurityContextHolder.setContext(context);
                    task.run();
                } finally {
                    SecurityContextHolder.setContext(previous);
                }
            };
        });
        return pool;
    }

    /** 日志专用小容量线程池，不与付费模型调用争抢容量。 */
    @Bean("aiLogExecutor")
    public ThreadPoolTaskExecutor aiLogExecutor() {
        return pool("ai-log-", 2, 200);
    }

    /** 构建有界且不会在提交线程执行被拒绝任务的线程池。 */
    private ThreadPoolTaskExecutor pool(String prefix, int workers, int queue) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(workers);
        pool.setMaxPoolSize(workers);
        pool.setQueueCapacity(queue);
        pool.setThreadNamePrefix(prefix);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(30);
        return pool;
    }
}
