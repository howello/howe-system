package com.howe.share.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 分享缓存专用线程池
 *
 * <p>刻意不复用框架共享的 {@code threadPoolTaskExecutor}（core 50）：WebDAV 源（如坚果云）
 * 有请求频率限制，用大并发会把源打爆。这里固定 3 个并发线程、队列 50，
 * 队列满时由调用线程兜底执行（CallerRunsPolicy），配合定时补偿任务保证不丢缓存任务。</p>
 *
 * @author howe
 */
@Configuration
public class ShareCacheExecutorConfig {

    /** 线程池 Bean 名，服务层按名注入 */
    public static final String EXECUTOR_BEAN = "shareCacheExecutor";

    /**
     * 分享缓存线程池
     *
     * @return 线程池
     */
    @Bean(name = EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor shareCacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("share-cache-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
