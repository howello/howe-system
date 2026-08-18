package com.howe.ai;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启 AI Outbox 与过期租约 Recovery 的定时调度。 */
@Configuration
@EnableScheduling
public class AiSchedulingConfig {
}
