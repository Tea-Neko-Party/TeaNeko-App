package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用受控时钟验证无租约读取的真实调度退避，不依靠 sleep 或真实数据库故障。
 * <br>2026/11/06：覆盖目录失败、分区隔离、成功恢复、取消及有界退避状态。
 *
 * <br>2026/11/07：测试宿主显式管理插件后台生命周期。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@Timeout(10)
class LayeredMemoryScanTest {
    /**
     * 长期领取失败不拦住本分区短期压缩检查和后续分区；成功后恢复普通轮询。
     * @throws Exception 当一次有界扫描未结束时
     */
    @Test
    void failedLongClaimBacksOffWithoutBlockingOtherReads() throws Exception {
        try (var h = new Harness()) {
            when(h.longs.claim(eq(h.first), any())).thenReturn(
                    CompletableFuture.failedStage(new IllegalStateException("私密持久字段内容")));
            h.drainAt(0);
            h.drainAt(5);
            verify(h.longs, times(1)).claim(eq(h.first), any());
            verify(h.longs, times(2)).claim(eq(h.second), any());
            verify(h.layers, times(2)).claim(eq(h.first), any(), any());
            verify(h.logger, times(1)).warn(eq("Agent 分层记忆"), contains("长期租约领取"));
            verify(h.logger, never()).warn(anyString(), contains("私密"));
            verify(h.logger, never()).warn(anyString(), contains("三次"));
            h.drainAt(30);
            h.drainAt(89);
            verify(h.longs, times(2)).claim(eq(h.first), any());
            when(h.longs.claim(eq(h.first), any())).thenReturn(CompletableFuture.completedStage(Optional.empty()));
            h.drainAt(90);
            h.drainAt(91);
            verify(h.longs, times(4)).claim(eq(h.first), any());
            when(h.longs.claim(eq(h.first), any())).thenReturn(CompletableFuture.failedStage(new IllegalStateException("再次读取失败")));
            h.drainAt(92);
            verify(h.logger).warn(anyString(), contains(h.start.plusSeconds(122).toString()));
            verifyNoInteractions(h.compressor, h.consolidator);
            verify(h.longs, never()).retry(any(), any(), any());
            verify(h.layers, never()).commit(any(), any(), any());
        }
    }

    /**
     * 中期领取失败独立退避，另一个分区仍能继续领取，不共享错误计数。
     * @throws Exception 当有界扫描失败时
     */
    @Test
    void mediumClaimFailureDoesNotSuppressHealthyPartition() throws Exception {
        try (var h = new Harness()) {
            when(h.layers.claim(eq(h.first), any(), any())).thenThrow(new IllegalStateException("同步读取失败"));
            h.drainAt(0);
            h.drainAt(5);
            verify(h.layers, times(1)).claim(eq(h.first), any(), any());
            verify(h.layers, times(2)).claim(eq(h.second), any(), any());
            verify(h.logger, times(1)).warn(anyString(), contains("中期租约领取"));
        }
    }

    /**
     * 目录不可读时不进入领取，冷却内无读库或重复日志，恢复后可继续扫描。
     * @throws Exception 当有界扫描失败时
     */
    @Test
    void directoryFailureHasBackoffAndResumesAfterRecovery() throws Exception {
        try (var h = new Harness()) {
            when(h.layers.streams(anyString())).thenReturn(CompletableFuture.failedStage(new IllegalStateException("目录不可读")));
            h.drainAt(0);
            h.drainAt(5);
            verify(h.layers, times(1)).streams(anyString());
            verify(h.layers, never()).claim(any(), any(), any());
            verifyNoInteractions(h.longs);
            verify(h.logger, times(1)).warn(anyString(), contains("阶段=目录扫描"));
            when(h.layers.streams(anyString())).thenReturn(CompletableFuture.completedStage(List.of(h.first, h.second)));
            h.drainAt(30);
            h.drainAt(31);
            verify(h.layers, times(3)).streams(anyString());
            verify(h.layers, times(2)).claim(eq(h.second), any(), any());
        }
    }

    /**
     * 尚未进入目录的配置故障保留失败结果，实际重试退避，恢复后解除整体冷却。
     * @throws Exception 当恢复后的有界扫描失败时
     */
    @Test
    void scanInitializationFailureKeepsFailureResultAndBacksOff() throws Exception {
        try (var h = new Harness()) {
            var failure = new IllegalStateException("配置读取失败");
            when(h.settings.get()).thenThrow(failure);
            var observed = assertThrows(ExecutionException.class, () -> h.drainAt(0));
            assertSame(failure, observed.getCause());
            h.drainAt(5);
            verify(h.settings, times(1)).get();
            verifyNoInteractions(h.layers, h.longs);
            verify(h.logger, times(1)).warn(anyString(), contains("阶段=扫描或退避保存"));
            doReturn(LayeredMemorySettings.defaults()).when(h.settings).get();
            h.drainAt(30);
            h.drainAt(31);
            verify(h.layers, times(2)).streams(anyString());
        }
    }

    /**
     * 关闭时取消仍传播为失败，不当成普通暂缓；迟到目录结果不再领取任何任务。
     * @throws Exception 当失败结果未及时到达时
     */
    @Test
    void closingPendingReadDoesNotBecomeSuccessfulDeferral() throws Exception {
        try (var h = new Harness()) {
            var directory = new CompletableFuture<List<MemoryLayers.Stream>>();
            when(h.layers.streams(anyString())).thenReturn(directory);
            var work = h.processor.drain().toCompletableFuture();
            // 2026/11/07：停用撤销结果资格，但原数据库读取结束前仍保持物理占用。
            var stopped = h.processor.stop().toCompletableFuture();
            assertFalse(work.isDone());
            assertFalse(stopped.isDone());
            directory.complete(List.of(h.first, h.second));
            var failure = assertThrows(ExecutionException.class, () -> work.get(2, TimeUnit.SECONDS));
            assertInstanceOf(ExecutionStoppedException.class, failure.getCause());
            stopped.get(2, TimeUnit.SECONDS);
            h.drainAt(1);
            verify(h.layers, times(1)).streams(anyString());
            verify(h.layers, never()).claim(any(), any(), any());
            verifyNoInteractions(h.longs);
            verify(h.logger, times(1)).warn(anyString(), contains("阶段=扫描或退避保存"));
        }
    }

    /** 大量失败目标达到容量后仍有限频，跟踪目标恢复后释放名额，不遗忘原数据。 */
    @Test
    void failureTrackingIsBoundedAndDelayIsCapped() {
        var delays = new MemoryScanBackoff();
        var now = Instant.parse("2026-11-06T00:00:00Z");
        for (int index = 0; index < 1024; index++) {
            delays.failed("分区" + index, now);
        }
        delays.failed("溢出", now);
        assertFalse(delays.allows("新分区", now.plusSeconds(1)));
        assertTrue(delays.allows("分区0", now.plusSeconds(30)));
        delays.succeeded("分区0");
        assertTrue(delays.allows("新分区", now.plusSeconds(1)));
        Instant next = now;
        for (int attempt = 0; attempt < 12; attempt++) {
            next = delays.failed("分区1", now);
        }
        assertEquals(now.plusSeconds(3600), next);
    }

    /**
     * 单在途调度夹具；每次返回同一有界页以直接观察各阶段的冷却次数。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.9.0-l
     */
    private static final class Harness implements AutoCloseable {
        final Instant start = Instant.parse("2026-11-06T00:00:00Z");
        final AtomicReference<Instant> now = new AtomicReference<>(start);
        final ILayeredMemoryStore layers = mock();
        final ILongTermMemoryStore longs = mock();
        final ILayeredMemoryCompressor compressor = mock();
        final ILongTermMemoryConsolidator consolidator = mock();
        final ILogger logger = mock();
        final Supplier<LayeredMemorySettings> settings = mock();
        final MemoryLayers.Stream first;
        final MemoryLayers.Stream second;
        final LayeredMemoryProcessor processor;

        /** 初始化原扫描控制和默认空领取，不启动定时线程或真实模型。 */
        Harness() {
            var streams = List.of(stream("坏分区"), stream("健康分区")).stream()
                    .sorted(Comparator.comparing(MemoryLayers.Stream::id)).toList();
            first = streams.getFirst();
            second = streams.getLast();
            Clock clock = mock();
            when(clock.instant()).thenAnswer(ignored -> now.get());
            ScheduledExecutorService scheduler = mock();
            ScheduledFuture<?> timer = mock();
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS))).thenAnswer(ignored -> timer);
            IAgentMemoryReferences references = mock();
            when(references.verify(any())).thenReturn(CompletableFuture.completedStage(null));
            when(layers.streams(anyString())).thenReturn(CompletableFuture.completedStage(streams));
            when(layers.claim(any(), any(), any())).thenReturn(CompletableFuture.completedStage(Optional.empty()));
            when(longs.claim(any(), any())).thenReturn(CompletableFuture.completedStage(Optional.empty()));
            when(settings.get()).thenReturn(LayeredMemorySettings.defaults());
            processor = new LayeredMemoryProcessor(layers, compressor, references, settings,
                    ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, scheduler, clock, logger,
                    longs, consolidator, ignored -> true);
            // 2026/11/07：独立测试宿主显式启用本代后台任务。
            processor.start().toCompletableFuture().join();
        }

        /**
         * @param name 测试分区
         * @return 符合真实引用校验的目录项
         */
        private static MemoryLayers.Stream stream(String name) {
            var reference = new MemoryNamespaceReference(new AgentScope("test", name), MemoryNamespaceId.SHARED, 0, 0);
            return new MemoryLayers.Stream(MemoryLayers.stream(reference), reference);
        }

        /**
         * @param seconds 相对测试起点的秒数
         * @throws Exception 当扫描未按期完成时
         */
        void drainAt(long seconds) throws Exception {
            now.set(start.plusSeconds(seconds));
            processor.drain().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }

        /** 只关闭处理器拥有的控制，不关闭借用调度器。 */
        @Override public void close() { processor.close(); }
    }
}
