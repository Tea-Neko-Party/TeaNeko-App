package org.zexnocs.teanekoagent.component.memory.layered;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.StyleObservations;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用受控时钟验证中长期整理的阶段计时、超时退避及在途占用，不连接数据库或模型。
 * <br>2026/11/06：沿用携带失败原因的退避接口，核对超时后的持久阶段仍被等待。
 * <br>2026/11/06：按有效新证据聚合长期提取，保留版本幂等、租约和事务校验。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.8.6-l
 */
@Timeout(5)
class LayeredMemoryDeadlineTest {
    /**
     * 扫描消耗大部分时限后，领取的模型任务仍取得完整时限。
     * @param longTerm 是否运行长期整理
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scanDoesNotConsumeGenerationTime(boolean longTerm) {
        try (var h = new Harness(longTerm)) {
            var work = h.processor.drain().toCompletableFuture();
            h.now.set(h.start.plusSeconds(44));
            h.scanned.complete(List.of(h.stream));
            h.now.set(h.start.plusSeconds(50));
            assertDoesNotThrow(h.control.get()::checkActive);
            h.finishGeneration();
            work.orTimeout(2, TimeUnit.SECONDS).join();
            if (longTerm) {
                verify(h.longs).commit(eq(h.longBatch), any(StyleObservations.Analysis.class), isNull(), any(), any());
            } else {
                verify(h.layers).commit(eq(h.batch), any(), any());
            }
            verifyNoInteractions(h.logger);
        }
    }

    /**
     * 真正超时时只报告批次原因，退避写入结束前不释放占用，迟到结果不能提交。
     * @param longTerm 是否运行长期整理
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutWaitsForRetryAndDoesNotReportScanFailure(boolean longTerm) {
        try (var h = new Harness(longTerm)) {
            h.processor.tick();
            h.scanned.complete(List.of(h.stream));
            h.now.set(h.start.plusSeconds(46));
            var stopped = assertThrows(ExecutionStoppedException.class, h.control.get()::checkActive);
            assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED, stopped.reason());
            h.processor.tick();
            verify(h.layers, times(1)).streams(anyString());
            verifyNoInteractions(h.logger);

            h.retry.complete(null);
            var lease = longTerm ? h.longBatch.leaseId() : h.batch.leaseId();
            verify(h.logger, times(1)).warn(anyString(), contains("批次=" + lease));
            verify(h.logger, never()).warn(anyString(), contains("目录扫描或退避"));
            h.finishGeneration();
            verify(h.layers, never()).commit(any(), any(), any());
            verify(h.longs, never()).commit(any(), any(StyleObservations.Analysis.class), any(), any(), any());

            when(h.layers.streams(anyString())).thenReturn(CompletableFuture.completedStage(List.of()));
            h.processor.drain().toCompletableFuture().orTimeout(2, TimeUnit.SECONDS).join();
            verify(h.layers, times(2)).streams(anyString());
        }
    }

/**
     * 借用模拟时钟与调度器，只显式触发截止检查，不创建等待线程。
     * <br>2026/11/06：按有效新证据聚合长期提取，保留版本幂等、租约和事务校验。
     *
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.6-l
     */
    private static final class Harness implements AutoCloseable {
        final Instant start = Instant.parse("2026-11-05T07:00:00Z");
        final AtomicReference<Instant> now = new AtomicReference<>(start);
        final AtomicReference<ExecutionControl> control = new AtomicReference<>();
        final ILayeredMemoryStore layers = mock();
        final ILongTermMemoryStore longs = mock();
        final ILogger logger = mock();
        final CompletableFuture<List<MemoryLayers.Stream>> scanned = new CompletableFuture<>();
        final CompletableFuture<MemoryLayers.Digest> digest = new CompletableFuture<>();
        final CompletableFuture<List<LongMemories.Candidate>> candidates = new CompletableFuture<>();
        final CompletableFuture<Void> retry = new CompletableFuture<>();
        final MemoryLayers.Stream stream;
        final MemoryLayers.Batch batch;
        final LongMemories.Batch longBatch;
        final LayeredMemoryProcessor processor;

        /**
         * @param longTerm 是否优先领取长期批次
         */
        Harness(boolean longTerm) {
            var scope = new AgentScope("test", "时限回归");
            var reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
            stream = new MemoryLayers.Stream(MemoryLayers.stream(reference), reference);
            var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()),
                    "", "消息", "用户");
            var message = new HistoryMessage("消息", actor, HistoryMessage.Role.USER, "需要整理的原话", start, false, false);
            batch = new MemoryLayers.Batch(stream, UUID.randomUUID(), 1, "指纹",
                    List.of(new MemoryLayers.Message(1, message, "人格", start.plusSeconds(3600))));
            var segment = new MemoryLayers.Segment(UUID.randomUUID(), stream, 1, 1, "指纹",
                    new MemoryLayers.Digest("摘要", "待整理片段"), start, start, start);
            // 2026/11/06：真实模型阶段需要有效证据，空证据现在直接完成筛选。
            longBatch = new LongMemories.Batch(stream, UUID.randomUUID(), List.of(segment),
                    List.of(new LongMemories.Evidence("证据", 1, message)), "指纹");
            Clock clock = mock();
            when(clock.instant()).thenAnswer(call -> now.get());
            ScheduledExecutorService scheduler = mock();
            ScheduledFuture<?> timer = mock();
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS))).thenAnswer(call -> timer);
            IAgentMemoryReferences references = mock();
            when(references.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
            when(layers.streams(anyString())).thenReturn(scanned);
            when(layers.claim(eq(stream), any(), any())).thenReturn(CompletableFuture.completedStage(Optional.of(batch)));
            when(layers.commit(eq(batch), any(), any())).thenReturn(CompletableFuture.completedStage(true));
            when(layers.retry(eq(batch), any())).thenReturn(retry);
            when(longs.claim(eq(stream), any())).thenReturn(CompletableFuture.completedStage(Optional.of(longBatch)));
            when(longs.commit(eq(longBatch), any(StyleObservations.Analysis.class), isNull(), any(), any()))
                    .thenReturn(CompletableFuture.completedStage(true));
            // 2026/11/05：等待带安全分类的真实退避完成，超时不提前释放在途状态。
            when(longs.retry(eq(longBatch), any(), any())).thenReturn(retry);
            ILayeredMemoryCompressor compressor = (input, execution) -> {
                control.set(execution);
                return digest;
            };
            ILongTermMemoryConsolidator consolidator = (input, execution) -> {
                control.set(execution);
                return candidates;
            };
            processor = new LayeredMemoryProcessor(layers, compressor, references, LayeredMemorySettings::defaults,
                    ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, scheduler, clock, logger,
                    longTerm ? longs : null, longTerm ? consolidator : null, ignored -> true);
        }

        /** 提交迟到或正常完成的模拟模型结果，不直接完成处理器。 */
        void finishGeneration() {
            digest.complete(new MemoryLayers.Digest("摘要", "整理后的中期片段"));
            candidates.complete(List.of());
        }

        /** 释放处理器自有控制，不关闭借用的模拟调度器。 */
        @Override
        public void close() {
            processor.close();
        }
    }
}
