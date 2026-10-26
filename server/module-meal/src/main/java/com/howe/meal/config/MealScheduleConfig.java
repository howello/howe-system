package com.howe.meal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 点餐模块定时任务开关
 *
 * <p>订单接单超时兜底依赖 {@code @Scheduled}。其它模块也各自开启了调度，
 * 这里再显式声明一次，让 module-meal 不依赖其它模块是否在场，
 * 也避免将来移除某个模块时超时关闭静默失效。</p>
 *
 * @author howe
 */
@Configuration
@EnableScheduling
public class MealScheduleConfig {
}
