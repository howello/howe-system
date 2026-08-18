package com.howe.ai.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.howe.ai.event.AiRunEventService;
import com.howe.ai.event.RunEventStreamWriter;
import com.howe.ai.persistence.AiFactPersistenceService;

/** AI 运行时装配：事件流的轮询间隔与单次连接上限均可配置，避免长连接无限占用线程。 */
@Configuration
public class AiRuntimeConfig {
    @Bean
    public RunEventStreamWriter runEventStreamWriter(
            AiRunEventService events, AiFactPersistenceService persistence,
            @Value("${ai.event.poll-interval-ms:500}") long pollIntervalMillis,
            @Value("${ai.event.max-stream-seconds:600}") long maxStreamSeconds) {
        return new RunEventStreamWriter(events, persistence, Duration.ofMillis(pollIntervalMillis),
            Duration.ofSeconds(maxStreamSeconds), Clock.systemUTC());
    }
}
