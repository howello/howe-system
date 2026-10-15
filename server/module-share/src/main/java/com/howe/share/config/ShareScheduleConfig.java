package com.howe.share.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 分享模块定时任务开关
 *
 * <p>缓存补偿与清理依赖 {@code @Scheduled}。这里显式声明一次，让 module-share
 * 不依赖其它模块是否开启调度。</p>
 *
 * @author howe
 */
@Configuration
@EnableScheduling
public class ShareScheduleConfig {
}
