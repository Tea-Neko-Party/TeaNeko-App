package org.zexnocs.teanekoagent.component.memory.layered;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.zexnocs.teanekoagent.component.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.JsonLLMTokenEstimator;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.component.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentFactory;
import org.zexnocs.teanekoagent.runtime.AgentRegistry;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 用真实处理器、生成器、输入估算及交互预算验证大批次的准入与容量边界。
 *
 * 修改记录：<br>
 * 2026/11/04：覆盖超过旧固定额度的中长期输入、完整输出预留及超限批次不推进进度。
 *
 * <br>2026/11/04����֤�г������������̳�ģ�������ȣ��Ϸ������벻�ܾɳ���Լ����
 *
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.2-l
 * @version 4.8.0-l
 */
@Timeout(15)
class MemoryGenerationBudgetTest {
    /**
     * 通过真实调度与交互路径确认父子预算一致，模型较小输出限制仍然生效。
     *
     * 2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
     * @param longTerm 是否运行长期整理
     * @param configuredOutput 模型配置的输出上限
     * @param oversized 是否超过完整输入容量
     */
    @ParameterizedTest
    @CsvSource({"false,16384,false", "true,16384,false", "false,2048,false", "true,2048,false",
            "false,16384,true", "true,16384,true"})
    void processesLargeInputsWithinBoundedBudget(boolean longTerm, int configuredOutput, boolean oversized) {
        var mapper = new ObjectMapper();
        var settings = LayeredMemorySettings.defaults();
        var now = Instant.now();
        var scope = new AgentScope("test", "预算回归");
        var reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
        var stream = new MemoryLayers.Stream(MemoryLayers.stream(reference), reference);
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()),
                "", "真实输入", "用户");
        var message = new HistoryMessage("真实输入", actor, HistoryMessage.Role.USER,
                "用户讨论项目进展。".repeat(oversized ? 6000 : 1500), now, false, false);
        var batch = new MemoryLayers.Batch(stream, UUID.randomUUID(), 1, "输入指纹",
                List.of(new MemoryLayers.Message(1, message, "人格", now.plusSeconds(3600))));
        var digest = new MemoryLayers.Digest("项目进展", "用户讨论了项目进展。");
        var segment = new MemoryLayers.Segment(UUID.randomUUID(), stream, 1, 1, "输入指纹", digest, now, now, now);
        var longBatch = new LongMemories.Batch(stream, UUID.randomUUID(), List.of(segment),
                List.of(new LongMemories.Evidence("证据", 1, message)), "输入指纹");
        var references = mock(IAgentMemoryReferences.class);
        when(references.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
        var layers = mock(ILayeredMemoryStore.class);
        when(layers.streams("")).thenReturn(CompletableFuture.completedStage(List.of(stream)));
        when(layers.claim(eq(stream), any(), any())).thenReturn(CompletableFuture.completedStage(Optional.of(batch)));
        when(layers.commit(eq(batch), any(), any())).thenReturn(CompletableFuture.completedStage(true));
        when(layers.retry(eq(batch), any())).thenReturn(CompletableFuture.completedStage(null));
        var longs = mock(ILongTermMemoryStore.class);
        when(longs.claim(eq(stream), any())).thenReturn(CompletableFuture.completedStage(Optional.of(longBatch)));
        when(longs.commit(eq(longBatch), any(org.zexnocs.teanekoagent.component.personality.StyleObservations.Analysis.class),
                isNull(), any(), any())).thenReturn(CompletableFuture.completedStage(true));
        when(longs.retry(eq(longBatch), any())).thenReturn(CompletableFuture.completedStage(null));
        var models = mock(ILLMModelService.class);
        var options = LLMModelOptions.builder().provider("test").model("受控模型").maxTokens(configuredOutput).build();
        when(models.resolveOptions(any())).thenAnswer(call -> {
            ILLMPrompt prompt = call.getArgument(0);
            return prompt.getOptions();
        });
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
            ILLMPrompt prompt = call.getArgument(0);
            var input = new JsonLLMTokenEstimator(mapper).estimate(prompt).inputTokens();
            assertTrue(input > 16384, "回归输入必须超过原固定总预算。");
            // 2026/11/04：业务生成器必须完整继承模型输出额度，不额外限制为 8192。
            assertEquals(configuredOutput, Objects.requireNonNull(prompt.getOptions()).getMaxTokens(), "输出额度必须与模型配置一致。");
            var text = longTerm ? "[]" : mapper.writeValueAsString(digest);
            ILLMResult result = LLMResult.builder().model("受控模型").choices(List.of(LLMChoice.builder()
                    .index(0).finishReason("stop").message(LLMAssistantMessage.builder()
                            .contents(new LLMContentListBuilder().addText(text).build()).build()).build())).build();
            return new TaskFuture<>(mock(ILogger.class), "记忆预算回归模型", CompletableFuture.completedFuture(result));
        });
        try (var timers = Executors.newSingleThreadScheduledExecutor(); var registry = new AgentRegistry()) {
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(options), ModelCapabilities.textOnly(),
                    LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), mock(ILogger.class));
            var interactions = new LLMInteractionService(models, LLMInteractionSettings::defaults, Runnable::run,
                    timers, new JsonLLMTokenEstimator(mapper));
            try (var compressor = new LLMLayeredMemoryCompressor(() -> new AgentFactory(registry), gate, references,
                    interactions, () -> settings, ignored -> new LayeredMemoryPolicy(true, true, true), mapper, Runnable::run);
                 var consolidator = new LLMLongTermMemoryConsolidator(() -> new AgentFactory(registry), gate, references,
                         interactions, () -> settings, ignored -> true, mapper, Runnable::run);
                 var processor = new LayeredMemoryProcessor(layers, compressor, references, () -> settings,
                         ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers, Clock.systemUTC(),
                         mock(ILogger.class), longTerm ? longs : null, longTerm ? consolidator : null, ignored -> true)) {
                processor.drain().toCompletableFuture().join();
                verify(models, times(oversized ? 0 : 1)).call(any(ILLMPrompt.class), any(ExecutionPolicy.class));
                if (longTerm) {
                    verify(longs, times(oversized ? 0 : 1)).commit(eq(longBatch),
                            any(org.zexnocs.teanekoagent.component.personality.StyleObservations.Analysis.class), isNull(), any(), any());
                    verify(longs, times(oversized ? 1 : 0)).retry(eq(longBatch), any());
                } else {
                    verify(layers, times(oversized ? 0 : 1)).commit(eq(batch), any(), any());
                    verify(layers, times(oversized ? 1 : 0)).retry(eq(batch), any());
                }
            }
        }
    }
}
