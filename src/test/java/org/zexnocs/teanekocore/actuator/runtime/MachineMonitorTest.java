package org.zexnocs.teanekocore.actuator.runtime;

import com.sun.management.OperatingSystemMXBean;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 CPU 统计窗口、内存口径与能力缺失，不依赖测试机器的实际负载。
 *
 * 修改记录：<br>
 * 2026/11/05：验证磁盘已用与可用量口径，并覆盖容量未知的情况。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.8.1-l
 */
class MachineMonitorTest {
    /**
     * 首轮只建基准，同秒复用；后续 CPU 按时长和核数计算，物理内存不含交换空间。
     * 2026/11/05：磁盘已用按总量减空闲计算，不把进程不可用的保留空间算成已用。
     */
    @Test
    void samplesCpuIntervalsAndKeepsMemoryDefinitionsSeparate() {
        var os = mock(OperatingSystemMXBean.class);
        when(os.getAvailableProcessors()).thenReturn(4);
        when(os.getProcessCpuTime()).thenReturn(1_000_000_000L, 2_000_000_000L, -1L);
        when(os.getCpuLoad()).thenReturn(0.42, 0.42, -1.0);
        when(os.getTotalMemorySize()).thenReturn(1000L);
        when(os.getFreeMemorySize()).thenReturn(400L, 400L, -1L);
        var memory = memory();
        var runtime = mock(RuntimeMXBean.class);
        when(runtime.getUptime()).thenReturn(60_000L);
        var nanos = new AtomicLong(1_000_000_000L);
        var disk = mock(File.class);
        when(disk.getTotalSpace()).thenReturn(2000L, 2000L, 0L);
        when(disk.getFreeSpace()).thenReturn(800L);
        when(disk.getUsableSpace()).thenReturn(600L);
        var monitor = new MachineMonitor(os, memory, runtime,
                Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC), nanos::get, disk);
        var first = monitor.sample();
        assertTrue(first.cpuWarmingUp());
        assertNull(first.systemCpuPercent());
        assertNull(first.processCpuPercent());
        nanos.addAndGet(500_000_000L);
        assertSame(first, monitor.sample());
        nanos.addAndGet(1_500_000_000L);
        var second = monitor.sample();
        assertEquals(2000, second.cpuIntervalMillis());
        assertEquals(42.0, second.systemCpuPercent());
        assertEquals(12.5, second.processCpuPercent());
        assertEquals(600L, second.physicalUsedBytes());
        assertEquals(1000L, second.physicalTotalBytes());
        assertEquals(128, second.heapUsedBytes());
        assertEquals(20, second.nonHeapUsedBytes());
        assertNull(second.processResidentBytes());
        assertEquals(1200L, second.diskUsedBytes());
        assertEquals(2000L, second.diskTotalBytes());
        assertEquals(600L, second.diskUsableBytes());
        nanos.addAndGet(2_000_000_000L);
        var unsupported = monitor.sample();
        assertFalse(unsupported.cpuWarmingUp());
        assertNull(unsupported.systemCpuPercent());
        assertNull(unsupported.processCpuPercent());
        assertNull(unsupported.physicalUsedBytes());
        assertNull(unsupported.diskUsedBytes());
        assertNull(unsupported.diskTotalBytes());
        assertNull(unsupported.diskUsableBytes());
    }

    /** 没有扩展系统管理接口时，JVM 指标仍可读取，未知容量和 CPU 不伪装成零。 */
    @Test
    void standardJvmWithoutExtendedMetricsRetainsSupportedValues() {
        var os = mock(java.lang.management.OperatingSystemMXBean.class);
        when(os.getAvailableProcessors()).thenReturn(1);
        var memory = memory();
        when(memory.getHeapMemoryUsage()).thenReturn(new MemoryUsage(0, 128, 256, -1));
        var monitor = new MachineMonitor(os, memory, mock(RuntimeMXBean.class), Clock.systemUTC(), () -> 1L);
        var value = monitor.sample();
        assertFalse(value.cpuWarmingUp());
        assertNull(value.systemCpuPercent());
        assertNull(value.physicalTotalBytes());
        assertNull(value.heapMaxBytes());
        assertEquals(128, value.heapUsedBytes());
    }

    /** @return 已用、已提交及上限分别取值的 JDK 内存来源 */
    private MemoryMXBean memory() {
        var memory = mock(MemoryMXBean.class);
        when(memory.getHeapMemoryUsage()).thenReturn(new MemoryUsage(0, 128, 256, 512));
        when(memory.getNonHeapMemoryUsage()).thenReturn(new MemoryUsage(0, 20, 40, -1));
        return memory;
    }
}
