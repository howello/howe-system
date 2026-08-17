package com.howe.ai.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Worker 对 Run 的短期租约，过期后不得继续写入终态。 */
public record RunLease(String runId, String workerId, Instant leaseUntil) {
    public RunLease {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("运行标识不能为空");
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("Worker 标识不能为空");
        if (leaseUntil == null) throw new NullPointerException("租约截止时间不能为空");
    }

    public boolean isExpired(Clock clock) {
        return !leaseUntil.isAfter(Instant.now(clock));
    }

    public RunLease renew(Duration extension, Clock clock) {
        if (extension == null || extension.isNegative() || extension.isZero()) {
            throw new IllegalArgumentException("租约续期必须为正数");
        }
        Instant base = leaseUntil.isAfter(Instant.now(clock)) ? leaseUntil : Instant.now(clock);
        return new RunLease(runId, workerId, base.plus(extension));
    }
}
