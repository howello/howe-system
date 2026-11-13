package com.howe.mcp.operation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内执行许可。
 *
 * <p>区分总许可、按账号许可与按分组许可；只有真正持有许可的任务才会被领取执行。
 * 这是<b>单进程容量控制</b>，不提供集群总并发或集群单用户互斥保证。
 * 许可只在任务线程退出的 finally 中释放，租约到期不提前归还。</p>
 */
public final class McpOperationExecutionLimiter {

    private final Semaphore total;

    private final int perUser;

    private final Map<String, GroupLimit> groups = new ConcurrentHashMap<>();

    private final Map<Long, AtomicInteger> userUsage = new ConcurrentHashMap<>();

    /**
     * 构造限制器。
     *
     * @param total 总并发上限
     * @param perUser 单账号并发上限
     * @param groupLimits 分组合并发上限
     */
    public McpOperationExecutionLimiter(int total, int perUser, Map<String, Integer> groupLimits) {
        this.total = new Semaphore(total);
        this.perUser = perUser;
        if (groupLimits != null) {
            groupLimits.forEach((group, limit) -> groups.put(group, new GroupLimit(limit)));
        }
    }

    /**
     * 尝试获取一次执行许可，不阻塞。
     *
     * @param userId 账号
     * @param group 队列分组
     * @return 许可票据；获取失败返回 {@code null}
     */
    public Permit tryAcquire(Long userId, String group) {
        GroupLimit groupLimit = groups.get(group);
        if (groupLimit != null && !groupLimit.tryAcquire()) {
            return null;
        }
        AtomicInteger usage = userUsage.computeIfAbsent(userId, key -> new AtomicInteger());
        while (true) {
            int current = usage.get();
            if (current >= perUser) {
                releaseGroup(groupLimit);
                return null;
            }
            if (usage.compareAndSet(current, current + 1)) {
                break;
            }
        }
        if (!total.tryAcquire()) {
            usage.decrementAndGet();
            releaseGroup(groupLimit);
            return null;
        }
        return new Permit(userId, groupLimit);
    }

    /** 归还分组许可。 */
    private static void releaseGroup(GroupLimit groupLimit) {
        if (groupLimit != null) {
            groupLimit.release();
        }
    }

    /** 当前可用总许可，供测试与诊断。 */
    public int availableTotal() {
        return total.availablePermits();
    }

    /** 分组许可。 */
    private final class GroupLimit {
        private final Semaphore semaphore;

        /** 构造分组许可。 */
        private GroupLimit(int limit) {
            this.semaphore = new Semaphore(limit);
        }

        /** 非阻塞获取。 */
        private boolean tryAcquire() {
            return semaphore.tryAcquire();
        }

        /** 归还。 */
        private void release() {
            semaphore.release();
        }
    }

    /** 一次执行许可；关闭时归还全部计数。 */
    public final class Permit implements AutoCloseable {

        private final Long userId;

        private final GroupLimit groupLimit;

        private boolean released;

        /** 记录归属。 */
        private Permit(Long userId, GroupLimit groupLimit) {
            this.userId = userId;
            this.groupLimit = groupLimit;
        }

        /** 归还总许可、账号许可与分组许可。 */
        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            total.release();
            AtomicInteger usage = userUsage.get(userId);
            if (usage != null) {
                usage.decrementAndGet();
            }
            releaseGroup(groupLimit);
        }
    }
}
