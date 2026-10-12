package com.howe.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 模块定时任务开关
 *
 * <p>生成图转存补偿依赖 {@code @Scheduled}。虽然博客模块也开了调度，这里再显式声明一次，
 * 让 module-ai 不依赖其它模块是否在场，也避免将来移除博客模块时补偿静默失效。</p>
 *
 * @author howe
 */
@Configuration
@EnableScheduling
public class AiScheduleConfig
{
}
