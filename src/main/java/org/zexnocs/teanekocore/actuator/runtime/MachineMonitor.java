package org.zexnocs.teanekocore.actuator.runtime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * 按需采样 JDK 运行指标，最短间隔一秒，不启动后台线程，不依赖聊天平台或业务插件。
 * 首次 CPU 采样只建立基准；未知值保持为空，不用虚拟地址空间冒充进程常驻内存。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@Component
public final class MachineMonitor {
    private static final long MIN_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();
    private final OperatingSystemMXBean operatingSystem;
    private final com.sun.management.@Nullable OperatingSystemMXBean extendedSystem;
    private final MemoryMXBean memory;
    private final RuntimeMXBean runtime;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private @Nullable Snapshot current;
    private long sampledNanos;
    private long processNanos = -1;

    /** 从当前 JVM 借用管理接口，不访问文件或启动调度任务。 */
    public MachineMonitor() {
        this(ManagementFactory.getOperatingSystemMXBean(), ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getRuntimeMXBean(), Clock.systemUTC(), System::nanoTime);
    }

    /**
     * 为测试及宿主组合提供明确的观测来源，借用对象的生命周期由调用方负责。
     * @param operatingSystem JDK 系统指标，扩展能力不支持时只保留通用指标
     * @param memory JVM 内存指标
     * @param runtime JVM 运行时长
     * @param clock 采样时间
     * @param nanoTime 单调时钟，用于 CPU 间隔与采样节流
     */
    public MachineMonitor(OperatingSystemMXBean operatingSystem, MemoryMXBean memory, RuntimeMXBean runtime,
            Clock clock, LongSupplier nanoTime) {
        this.operatingSystem = operatingSystem;
        this.extendedSystem = operatingSystem instanceof com.sun.management.OperatingSystemMXBean bean ? bean : null;
        this.memory = memory;
        this.runtime = runtime;
        this.clock = clock;
        this.nanoTime = nanoTime;
    }

    /**
     * 同一秒内复用不可变采样，防止并发页面把 CPU 统计窗口缩短到没有意义。
     * 进程 CPU 为两次采样间 CPU 时间除以经过时间和可用处理器数。
     * @return 带真实观测时间的快照，不包含主机名、路径或其他进程信息
     */
    public synchronized Snapshot sample() {
        long now = nanoTime.getAsLong();
        long elapsed = now - sampledNanos;
        if (current != null && elapsed < MIN_INTERVAL_NANOS) {
            return current;
        }
        int processors = operatingSystem.getAvailableProcessors();
        boolean warmingUp = current == null && extendedSystem != null;
        long cpu = extendedSystem == null ? -1 : extendedSystem.getProcessCpuTime();
        Double systemPercent = extendedSystem == null ? null : percent(extendedSystem.getCpuLoad());
        Double processPercent = !warmingUp && cpu >= 0 && processNanos >= 0 && cpu >= processNanos && elapsed > 0
                ? Math.min(100.0, (cpu - processNanos) * 100.0 / elapsed / processors) : null;
        Long total = extendedSystem == null ? null : positive(extendedSystem.getTotalMemorySize());
        long free = extendedSystem == null ? -1 : extendedSystem.getFreeMemorySize();
        Long used = total != null && free >= 0 && free <= total ? total - free : null;
        var heap = memory.getHeapMemoryUsage();
        var nonHeap = memory.getNonHeapMemoryUsage();
        current = new Snapshot(clock.instant(), runtime.getUptime(), processors, warmingUp,
                current == null ? 0 : Duration.ofNanos(elapsed).toMillis(), warmingUp ? null : systemPercent,
                processPercent, heap.getUsed(), heap.getCommitted(), positive(heap.getMax()),
                nonHeap.getUsed(), nonHeap.getCommitted(), used, total, null);
        sampledNanos = now;
        processNanos = cpu;
        return current;
    }

    /**
     * @param load JDK 最近窗口负载，负值表示不支持
     * @return 有效百分比，未知或无效数据为空
     */
    private static @Nullable Double percent(double load) {
        return Double.isFinite(load) && load >= 0 && load <= 1 ? load * 100 : null;
    }

    /**
     * @param value JDK 返回的容量，非正值表示没有有效上限或不支持
     * @return 有效字节数
     */
    private static @Nullable Long positive(long value) {
        return value > 0 ? value : null;
    }

    /**
     * 当前采样的指标口径，JVM 容量与物理内存分别命名。
     * JDK 未公开进程常驻内存，当前实现明确返回空值。
     *
     * @param observedAt 真实采样时间
     * @param uptimeMillis JVM 运行时长
     * @param processors JVM 可用处理器数
     * @param cpuWarmingUp 是否首次建立 CPU 基准
     * @param cpuIntervalMillis 距离上次实际采样的时长，首次为零
     * @param systemCpuPercent 系统 CPU 最近窗口百分比
     * @param processCpuPercent 当前进程 CPU 间隔平均百分比，按可用处理器数归一化
     * @param heapUsedBytes JVM 堆已用量
     * @param heapCommittedBytes JVM 堆已提交量，不等于常驻物理内存
     * @param heapMaxBytes JVM 堆上限，未定义时为空
     * @param nonHeapUsedBytes JVM 非堆已用量
     * @param nonHeapCommittedBytes JVM 非堆已提交量
     * @param physicalUsedBytes 系统物理内存已用量，不含交换空间
     * @param physicalTotalBytes 系统物理内存总量
     * @param processResidentBytes 当前进程常驻物理内存，不支持时为空
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public record Snapshot(Instant observedAt, long uptimeMillis, int processors, boolean cpuWarmingUp,
                           long cpuIntervalMillis, @Nullable Double systemCpuPercent, @Nullable Double processCpuPercent,
                           long heapUsedBytes, long heapCommittedBytes, @Nullable Long heapMaxBytes,
                           long nonHeapUsedBytes, long nonHeapCommittedBytes, @Nullable Long physicalUsedBytes,
                           @Nullable Long physicalTotalBytes, @Nullable Long processResidentBytes) {}
}
