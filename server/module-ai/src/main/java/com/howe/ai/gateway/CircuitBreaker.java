package com.howe.ai.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 模型渠道的轻量熔断器，OPEN 冷却后只放行一次 HALF_OPEN 探测。 */
public class CircuitBreaker {
    private final int failureThreshold;
    private final Duration cooldown;
    private Clock clock;
    private int failures;
    private Instant openedAt;
    private boolean halfOpenProbe;

    public CircuitBreaker(int failureThreshold, Duration cooldown, Clock clock) {
        if (failureThreshold < 1) throw new IllegalArgumentException("熔断阈值必须为正数");
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            throw new IllegalArgumentException("熔断冷却时间必须为正数");
        }
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public boolean allowRequest() {
        if (openedAt == null) return true;
        if (Instant.now(clock).isBefore(openedAt.plus(cooldown))) return false;
        if (halfOpenProbe) return false;
        halfOpenProbe = true;
        return true;
    }

    public void recordFailure() {
        if (halfOpenProbe) {
            openedAt = Instant.now(clock);
            halfOpenProbe = false;
            return;
        }
        failures++;
        if (failures >= failureThreshold) openedAt = Instant.now(clock);
    }

    public void recordSuccess() {
        failures = 0;
        openedAt = null;
        halfOpenProbe = false;
    }

    public CircuitBreaker withClock(Clock newClock) {
        CircuitBreaker copy = new CircuitBreaker(failureThreshold, cooldown, newClock);
        copy.failures = failures;
        copy.openedAt = openedAt;
        copy.halfOpenProbe = halfOpenProbe;
        return copy;
    }
}
