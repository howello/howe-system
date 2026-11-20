package com.howe.pregnancy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 孕期任务执行配置
 *
 * <p>扫描线程只负责领取任务，耗时操作交给有界执行器，避免长时间 IO 占住调度线程。
 * 企微发送和导出各自保留容量，防止单类任务挤占全部工作线程。</p>
 *
 * @author howe
 */
@Configuration
@EnableScheduling
public class PregnancyTaskConfig {

    /**
     * 孕期后台任务执行器
     *
     * @return 有界执行器
     */
    @Bean(name = "pregnancyTaskExecutor")
    public Executor pregnancyTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("preg-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
