package com.howe.framework.tool.service.impl;

import com.howe.framework.tool.dto.SystemHealthDtos;
import com.howe.framework.tool.service.ISystemHealthReadService;
import java.lang.management.ManagementFactory;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.util.Util;
import org.springframework.stereotype.Service;

/**
 * 服务器健康摘要读取服务实现。
 *
 * <p>只采集 CPU、物理内存与本机文件系统使用比例，复用既有 OSHI 依赖，
 * 不构造包含主机名、IP、路径或 JVM 参数的对象。CPU 采样使用可中断的短等待，
 * 采样线程被中断时立即退出并抛出异常。</p>
 */
@Service
public class SystemHealthReadServiceImpl implements ISystemHealthReadService {

    /** CPU 采样窗口毫秒数。 */
    private static final long SAMPLE_MILLIS = 1000L;

    /** 时间输出格式。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 采集一次健康摘要。
     */
    @Override
    public SystemHealthDtos sample() {
        try {
            SystemInfo info = new SystemInfo();
            HardwareAbstractionLayer hardware = info.getHardware();
            double cpu = cpuPercent(hardware.getProcessor());
            double memory = memoryRatio(hardware.getMemory());
            double disk = diskRatio(info);
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            String sampledAt = TIME_FORMAT.format(ZonedDateTime.now(ZoneId.systemDefault()));
            return new SystemHealthDtos(sampledAt, cpu, memory, disk, uptime);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("健康采样被中断", exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("健康采样失败", exception);
        }
    }

    /**
     * 计算 CPU 使用百分比。
     *
     * <p>取两次 tick 差值，总 tick 为 0 时视为无效采样并失败。</p>
     *
     * @param processor 处理器
     * @return 使用百分比，0—100
     * @throws InterruptedException 等待被中断
     */
    private static double cpuPercent(CentralProcessor processor) throws InterruptedException {
        long[] previous = processor.getSystemCpuLoadTicks();
        Thread.sleep(SAMPLE_MILLIS);
        long[] current = processor.getSystemCpuLoadTicks();
        long total = 0L;
        long idle = 0L;
        for (CentralProcessor.TickType type : CentralProcessor.TickType.values()) {
            int index = type.getIndex();
            long delta = current[index] - previous[index];
            total += delta;
            if (type == CentralProcessor.TickType.IDLE) {
                idle += delta;
            }
        }
        if (total <= 0L) {
            throw new IllegalStateException("CPU 采样无效");
        }
        double busy = (double) (total - idle) / total * 100.0D;
        return clamp(busy, 0.0D, 100.0D);
    }

    /**
     * 计算物理内存使用比例。
     *
     * @param memory 内存信息
     * @return 使用比例，0—1
     */
    private static double memoryRatio(GlobalMemory memory) {
        long total = memory.getTotal();
        if (total <= 0L) {
            throw new IllegalStateException("内存采样无效");
        }
        long used = total - memory.getAvailable();
        return clamp((double) used / total, 0.0D, 1.0D);
    }

    /**
     * 计算本地文件系统使用比例的最大值。
     *
     * <p>只统计本地存储，跳过网络与虚拟文件系统，避免触发远端探测；
     * 没有任何有效样本时失败，不伪造 0。</p>
     *
     * @param info 系统信息
     * @return 使用比例，0—1
     */
    private static double diskRatio(SystemInfo info) {
        double max = -1.0D;
        for (OSFileStore store : info.getOperatingSystem().getFileSystem().getFileStores()) {
            if (!isLocal(store)) {
                continue;
            }
            long total = store.getTotalSpace();
            if (total <= 0L) {
                continue;
            }
            long usable = store.getUsableSpace();
            double ratio = clamp((double) (total - usable) / total, 0.0D, 1.0D);
            if (ratio > max) {
                max = ratio;
            }
        }
        if (max < 0.0D) {
            throw new IllegalStateException("磁盘采样无效");
        }
        return max;
    }

    /**
     * 判断文件系统是否为本地存储。
     *
     * <p>按类型名与挂载点排除网络/虚拟文件系统；判定保守，宁可少统计也不触发远端探测。</p>
     *
     * @param store 文件系统
     * @return 本地存储返回 true
     */
    private static boolean isLocal(OSFileStore store) {
        String type = store.getType() == null ? "" : store.getType().toLowerCase(java.util.Locale.ROOT);
        if (type.startsWith("nfs") || type.startsWith("cifs") || type.startsWith("smb") || type.startsWith("fuse")
                || type.startsWith("overlay") || type.startsWith("tmpfs") || type.startsWith("devfs")
                || type.startsWith("proc") || type.startsWith("sysfs") || type.startsWith("aufs")) {
            return false;
        }
        String mount = store.getMount() == null ? "" : store.getMount();
        return !mount.startsWith("/proc") && !mount.startsWith("/sys") && !mount.startsWith("/dev");
    }

    /**
     * 把数值限制在闭区间内。
     *
     * @param value 原值
     * @param minimum 下界
     * @param maximum 上界
     * @return 限制后的值
     */
    private static double clamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
