package com.howe.framework.tool.dto;

/**
 * 服务器健康摘要的对外数据载体。
 *
 * <p>只包含采样时间、CPU 使用比例、内存使用比例、磁盘使用比例与 JVM 运行时长。
 * 不包含主机名、IP、挂载点、设备名、路径、JVM 参数或进程命令。</p>
 *
 * @param sampledAt 采样时间，带偏移量的 ISO-8601
 * @param cpuPercent CPU 使用百分比，0—100
 * @param memoryUsageRatio 物理内存使用比例，0—1
 * @param diskUsageRatio 本地文件系统使用比例的最大值，0—1
 * @param uptime JVM 已运行毫秒数
 */
public record SystemHealthDtos(String sampledAt, double cpuPercent, double memoryUsageRatio, double diskUsageRatio,
        long uptime) {
}
