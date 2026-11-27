package com.howe.book.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 微信读书同步独立在一个后台线程中顺序拉取和处理。 */
@Configuration
@EnableScheduling
public class BookWeReadSyncConfig {
    public static final String EXECUTOR = "bookWeReadExecutor";

    /** 创建独立线程池，任务拒绝时直接反馈给请求方。 */
    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor bookWeReadExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("book-weread-");
        return executor;
    }
}
