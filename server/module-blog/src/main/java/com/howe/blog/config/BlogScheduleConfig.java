package com.howe.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 博客模块定时任务开关。
 *
 * <p>原定时能力由 module-quartz 提供，该模块移除后改由 Spring {@code @Scheduled} 承载，
 * 需要在此显式开启调度。</p>
 *
 * @author howe
 */
@Configuration
@EnableScheduling
public class BlogScheduleConfig {
}
