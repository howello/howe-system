package com.howe.framework.web.domain.server;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import com.howe.common.utils.Arith;
import com.howe.common.utils.DateUtils;

/**
 * JVM相关信息
 *
 * @author howe
 */
public class Jvm
{
    /**
     * 当前JVM占用的内存总数(M)
     */
    private double total;

    /**
     * JVM最大可用内存总数(M)
     */
    private double max;

    /**
     * JVM空闲内存(M)
     */
    private double free;

    /**
     * JDK版本
     */
    private String version;

    /**
     * JDK路径
     */
    private String home;

    public double getTotal()
    {
        return Arith.div(total, (1024 * 1024), 2);
    }

    public void setTotal(double total)
    {
        this.total = total;
    }

    public double getMax()
    {
        return Arith.div(max, (1024 * 1024), 2);
    }

    public void setMax(double max)
    {
        this.max = max;
    }

    public double getFree()
    {
        return Arith.div(free, (1024 * 1024), 2);
    }

    public void setFree(double free)
    {
        this.free = free;
    }

    public double getUsed()
    {
        return Arith.div(total - free, (1024 * 1024), 2);
    }

    public double getUsage()
    {
        return Arith.mul(Arith.div(total - free, total, 4), 100);
    }

    /**
     * 获取JDK名称
     */
    public String getName()
    {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getHome()
    {
        return home;
    }

    public void setHome(String home)
    {
        this.home = home;
    }

    /**
     * JDK启动时间
     */
    public String getStartTime()
    {
        return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, DateUtils.getServerStartDate());
    }

    /**
     * JDK运行时间
     */
    public String getRunTime()
    {
        return DateUtils.timeDistance(DateUtils.getNowDate(), DateUtils.getServerStartDate());
    }

    /**
     * 运行参数
     */
    public String getInputArgs()
    {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
    }

    /**
     * JVM 非堆内存：当前占用（M）
     *
     * @return 非堆已用内存（MB）
     */
    public double getNonHeapUsed()
    {
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        return Arith.div(nonHeap.getUsed(), (1024 * 1024), 2);
    }

    /**
     * JVM 非堆内存：已提交总量（M），作为非堆的「上限」参考
     *
     * @return 非堆已提交内存（MB）
     */
    public double getNonHeapTotal()
    {
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long committed = nonHeap.getCommitted() > 0 ? nonHeap.getCommitted() : nonHeap.getMax();
        return Arith.div(committed, (1024 * 1024), 2);
    }

    /**
     * JVM 非堆内存使用率（%）
     *
     * @return 使用率百分比；总量为 0 时返回 0
     */
    public double getNonHeapUsage()
    {
        long committed = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getCommitted();
        if (committed <= 0)
        {
            return 0;
        }
        long used = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
        return Arith.mul(Arith.div(used, committed, 4), 100);
    }

    /**
     * GC 总次数（所有垃圾回收器累计）
     *
     * @return GC 次数
     */
    public long getGcCount()
    {
        long count = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans())
        {
            if (gc.getCollectionCount() > 0)
            {
                count += gc.getCollectionCount();
            }
        }
        return count;
    }

    /**
     * GC 总耗时（毫秒，所有垃圾回收器累计）
     *
     * @return GC 耗时（毫秒）
     */
    public long getGcTime()
    {
        long time = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans())
        {
            if (gc.getCollectionTime() > 0)
            {
                time += gc.getCollectionTime();
            }
        }
        return time;
    }
}
