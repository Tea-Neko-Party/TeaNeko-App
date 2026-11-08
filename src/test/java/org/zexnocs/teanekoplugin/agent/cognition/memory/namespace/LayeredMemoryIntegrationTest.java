package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemoryPolicy;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemorySettings;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryProcessingException;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemoryProcessor;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.LayeredMemoryPromptSource;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryMessage;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.adapter.EasyDataHistoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.*;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用真实 EasyData 事务夹具验证短期采集、中期租约、恢复与迟到提交，外部模型不启动。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证结构化失败原因不泄漏原文，失败保留原消息并按租约退避。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：关闭处理器后等待实际退避结束，迟到模型结果仍不得提交。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/06：适配真实类型化 JSON 服务，保留原领域与提交边界回归。
 * <br>2026/11/07：测试宿主显式管理插件后台生命周期。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@Timeout(20)
class LayeredMemoryIntegrationTest {
    /**
     * 后台模型失败保留实际终止状态，不记录包装异常中的私密原文。
     */
    @Test
    void reportsSafeModelFailureWithoutAdvancingMemory() {
        try (var harness = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
            for (int i = 1; i <= 8; i++) {
                harness.append(i);
            }
            var references = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            when(references.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
            var logger = mock(ILogger.class);
            var failure = new CompletionException("不得输出的私密原文", new MemoryProcessingException(
                    MemoryProcessingException.Reason.MODEL_RESULT,
                    org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.TIMED_OUT));
            try (var processor = new LayeredMemoryProcessor(harness.layers,
                    (batch, control) -> CompletableFuture.failedStage(failure), references, () -> settings,
                    ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers,
                    Clock.fixed(now, ZoneOffset.UTC), logger)) {
                // 2026/11/07：独立测试宿主显式启用本代后台任务。
                processor.start().toCompletableFuture().join();
                processor.drain().toCompletableFuture().join();
                var message = org.mockito.ArgumentCaptor.forClass(String.class);
                verify(logger).warn(eq("Agent 分层记忆"), message.capture());
                assertTrue(message.getValue().contains("中期模型生成"));
                assertTrue(message.getValue().contains("MODEL_RESULT"));
                assertTrue(message.getValue().contains("TIMED_OUT"));
                assertFalse(message.getValue().contains("不得输出"));
                var view = harness.layers.read(reference, now, settings.retention()).toCompletableFuture().join();
                assertEquals(0, view.compressedThrough());
                assertEquals(8, view.recent().size());
            }
        }
    }

    private final Instant now = Instant.parse("2026-11-02T00:00:00Z");
    private final AgentScope scope = new AgentScope("test", "群甲");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final LayeredMemorySettings settings = new LayeredMemorySettings(true, 6, 2, 4, 131072, Duration.ofSeconds(30), Duration.ofDays(90));
    /**
     * 装配同一事务中原历史及新分层存储，不创建新仓库替身。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.9.0-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final EasyDataLayeredMemoryStore layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
        final EasyDataHistoryStore history = new EasyDataHistoryStore(db.data, db.mapper, layers);
        /** 初始化实际共享空间，不绕过代次检查。 */
        Harness() { db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 10).toCompletableFuture().join(); }
        /**
         * 添加带精确事件时间的真实消息。
         * @param number 稳定来源编号
         */
        void append(int number) {
            history.append(context("人格一"), message(number), now.plus(Duration.ofDays(7))).toCompletableFuture().join();
        }
        /**
         * 读取已持久的唯一分区。
         * @return 原持久分区
         */
        MemoryLayers.Stream stream() { return layers.streams("").toCompletableFuture().join().getFirst(); }
        /** 关闭本夹具拥有的数据库执行器。 */
        @Override public void close() { db.close(); }
    }
    /**
     * 创建具有明确用户身份的原始消息。
     * @param number 稳定来源编号
     * @return 原始消息
     */
    private HistoryMessage message(int number) {
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(new UUID(0, 1)), "用户", "消息" + number, "群甲");
        return new HistoryMessage(actor.sourceId(), actor, HistoryMessage.Role.USER,
                "我们正在讨论项目部署，决定先确认数据库事务，再完善错误恢复和观察诊断。".repeat(5) + number, now.plusSeconds(number), false, false);
    }
    /**
     * 固定测试当前引用，人格变更不改变新短期分区。
     * @param identity 人格身份
     * @return 原历史授权边界
     */
    private HistoryContext context(String identity) {
        return new HistoryContext(reference, new AgentPersonalitySnapshot(identity, "teaneko", 1, "当前补正", 0, "基础人格", "", null));
    }
    /** 历史和短期记录一起回滚；服务重建后恢复消息，人格变更和来源重投不会重复创建。 */
    @Test void capturesAtomicallyAndRestoresOnlyCurrentScope() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.db.failingWritePrefix.set("expiry");
            assertThrows(CompletionException.class, () -> h.append(1));
            h.db.failingWritePrefix.set(null);
            assertTrue(h.layers.read(reference, now, settings.retention()).toCompletableFuture().join().recent().isEmpty());
            h.append(1);
            h.history.append(context("人格二"), message(1), now.plus(Duration.ofDays(7))).toCompletableFuture().join();
            var restored = new EasyDataLayeredMemoryStore(h.db.data, h.db.store, h.db.mapper);
            var view = restored.read(reference, now, settings.retention()).toCompletableFuture().join();
            assertEquals(1, view.recent().size()); assertEquals(now.plusSeconds(1), view.recent().getFirst().message().occurredAt());
            var other = new MemoryNamespaceReference(new AgentScope("test", "群乙"), MemoryNamespaceId.SHARED, 0, 0);
            assertTrue(restored.read(other, now, settings.retention()).toCompletableFuture().join().recent().isEmpty());
            var refs = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            when(refs.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
            var source = new LayeredMemoryPromptSource(restored, refs, () -> settings, ignored -> new LayeredMemoryPolicy(true, true, true),
                    h.db.mapper, Runnable::run, Clock.fixed(now, ZoneOffset.UTC));
            var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(new UUID(0, 1)), "用户", "新消息", "群甲");
            var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.COMMAND, List.of(), null, reference);
            var fragments = source.resolve(new AgentDefinition("测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.COMMAND)), request, null, control).toCompletableFuture().join();
            assertEquals("memory_recent", fragments.getFirst().name()); assertTrue(fragments.getFirst().text().contains("群甲"));
            assertTrue(fragments.getFirst().text().contains("2026-11-02T00:00:01Z"));
            var concurrent = new ArrayList<CompletableFuture<?>>();
            for (int i = 2; i <= 9; i++) {
                concurrent.add(h.history.append(context("人格二"), message(i), now.plus(Duration.ofDays(7))).toCompletableFuture());
            }
            CompletableFuture.allOf(concurrent.toArray(CompletableFuture[]::new)).join();
            var updated = restored.read(reference, now, settings.retention()).toCompletableFuture().join();
            assertEquals(9, updated.recent().size());
            assertEquals(9, updated.recent().stream().map(MemoryLayers.Message::sequence).distinct().count());
            assertEquals(9, updated.recent().getLast().sequence());
        }
    }
    /** 压缩领取跨服务重建恢复；新消息不妨碍旧前缀提交，旧租约不能覆盖重领结果。 */
    @Test void resumesLeaseAndCommitsExactPrefixWhileNewMessagesArrive() {
        try (var h = new Harness()) {
            for (int i = 1; i <= 8; i++) { h.append(i); }
            var byteTrigger = new LayeredMemorySettings(true, 100, 2, 4, 131072, Duration.ofSeconds(30), Duration.ofDays(90), 1024);
            var batch = h.layers.claim(h.stream(), byteTrigger, now).toCompletableFuture().join().orElseThrow();
            assertTrue(h.layers.claim(h.stream(), settings, now).toCompletableFuture().join().isEmpty());
            var restored = new EasyDataLayeredMemoryStore(h.db.data, h.db.store, h.db.mapper);
            var recovered = restored.claim(h.stream(), settings, now.plusSeconds(61)).toCompletableFuture().join().orElseThrow();
            h.append(9);
            var digest = new MemoryLayers.Digest("讨论部署", "已经决定先确认数据库事务，之后完善恢复及诊断；具体部署尚未执行。");
            assertFalse(h.layers.commit(batch, digest, now.plusSeconds(62)).toCompletableFuture().join());
            h.db.failingWritePrefix.set("medium:");
            assertThrows(CompletionException.class, () -> restored.commit(recovered, digest, now.plusSeconds(62)).toCompletableFuture().join());
            h.db.failingWritePrefix.set(null);
            assertEquals(0, restored.read(reference, now, settings.retention()).toCompletableFuture().join().compressedThrough());
            assertTrue(restored.commit(recovered, digest, now.plusSeconds(62)).toCompletableFuture().join());
            var view = restored.read(reference, now, settings.retention()).toCompletableFuture().join();
            assertEquals(4, view.compressedThrough()); assertEquals(9, view.recent().size()); assertEquals(1, view.segments().size());
            assertEquals(now.plusSeconds(1), view.segments().getFirst().occurredFrom());
            assertEquals(now.plusSeconds(4), view.segments().getFirst().occurredThrough());
        }
    }
    /** 共享空间清空后原租约不能提交，不能把旧片段改投新代次。 */
    @Test void clearRejectsLateCompression() {
        try (var h = new Harness()) {
            for (int i = 1; i <= 8; i++) { h.append(i); }
            var batch = h.layers.claim(h.stream(), settings, now).toCompletableFuture().join().orElseThrow();
            h.db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertFalse(h.layers.commit(batch, new MemoryLayers.Digest("旧片段", "不应交付"), now.plusSeconds(1)).toCompletableFuture().join());
        }
    }
    /**
     * 后台等待真实生成；等待中撤销策略则保留原消息且不推进水位，关闭不取消外部生成任务。
     * 2026/11/05：关闭后的停止已由批次退避处理，调用方等待其真实完成而非外层抢先抛出异常。
     */
    @Test void processorRejectsLateResultAfterPolicyChange() {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
            for (int i = 1; i <= 8; i++) { h.append(i); }
            var pending = new CompletableFuture<MemoryLayers.Digest>();
            var started = new CompletableFuture<Void>();
            var references = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            when(references.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
            var policy = new AtomicReference<>(new LayeredMemoryPolicy(true, true, true));
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, control) -> { started.complete(null); return pending; }, references, () -> settings,
                    ignored -> policy.get(), Runnable::run, timers, Clock.fixed(now, ZoneOffset.UTC), mock(ILogger.class))) {
                // 2026/11/07：独立测试宿主显式启用本代后台任务。
                processor.start().toCompletableFuture().join();
                var work = processor.drain().toCompletableFuture();
                started.orTimeout(5, TimeUnit.SECONDS).join();
                assertFalse(work.isDone()); policy.set(new LayeredMemoryPolicy(true, true, false));
                pending.complete(new MemoryLayers.Digest("不得采用", "学习资格已关闭"));
                work.join();
                var view = h.layers.read(reference, now, settings.retention()).toCompletableFuture().join();
                assertEquals(0, view.compressedThrough()); assertTrue(view.segments().isEmpty()); assertEquals(8, view.recent().size());
            }
            var late = new CompletableFuture<MemoryLayers.Digest>(); var restarted = new CompletableFuture<Void>();
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, control) -> { restarted.complete(null); return late; }, references, () -> settings,
                    ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers,
                    Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC), mock(ILogger.class))) {
                // 2026/11/07：独立测试宿主显式启用本代后台任务。
                processor.start().toCompletableFuture().join();
                var work = processor.drain().toCompletableFuture(); restarted.orTimeout(5, TimeUnit.SECONDS).join();
                processor.close();
                // 2026/11/07：停用撤销结果资格，但真实模型阶段结束前仍保留占用。
                assertFalse(work.isDone());
                assertFalse(late.isCancelled());
                late.complete(new MemoryLayers.Digest("关闭后结果", "不能交付"));
                work.orTimeout(5, TimeUnit.SECONDS).join();
                assertEquals(0, h.layers.read(reference, now, settings.retention()).toCompletableFuture().join().compressedThrough());
            }
        }
    }
    /** 默认 Spring 装配可延迟取得工厂，历史 Bean 自动加入分层事务，启动不调用模型。 */
    @Test void wiresDefaultSpringBeansWithoutStartingModels() {
        try (var h = new Harness(); var executor = Executors.newVirtualThreadPerTaskExecutor();
             var timers = Executors.newSingleThreadScheduledExecutor(); var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            var models = mock(org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService.class);
            var gate = mock(org.zexnocs.teanekoagent.runtime.access.AgentAccessGate.class);
            var references = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            var configs = mock(org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService.class);
            var beans = context.getBeanFactory();
            // 2026/11/06：局部装配复用真实 JSON 合同，启动仍不调用模型。
            beans.registerSingleton("structuredOutput", org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonTestSupport.create(models, h.db.mapper));
            beans.registerSingleton("agentData", h.db.data); beans.registerSingleton("namespaceStore", h.db.store);
            beans.registerSingleton("json", h.db.mapper); beans.registerSingleton("interactions", models); beans.registerSingleton("gate", gate);
            beans.registerSingleton("references", references); beans.registerSingleton("configs", configs);
            beans.registerSingleton("manager", new org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager());
            beans.registerSingleton("virtualExecutor", executor); beans.registerSingleton("taskExecutor", timers); beans.registerSingleton("logger", mock(ILogger.class));
            // 2026/11/07：按插件迁移后的组合根验证默认装配。
            beans.registerSingleton("coreTimers", mock(org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService.class));
            context.register(org.zexnocs.teanekoplugin.agent.cognition.memory.configuration.LayeredMemoryConfig.class, EasyDataHistoryStore.class);
            AgentComponentFixtures.register(context);
            context.refresh();
            context.getBean(EasyDataHistoryStore.class).append(context("人格一"), message(1), now.plus(Duration.ofDays(7))).toCompletableFuture().join();
            assertEquals(1, context.getBean(ILayeredMemoryStore.class).read(reference, now, settings.retention()).toCompletableFuture().join().recent().size());
            verifyNoInteractions(models, gate, references, configs);
        }
    }
}
