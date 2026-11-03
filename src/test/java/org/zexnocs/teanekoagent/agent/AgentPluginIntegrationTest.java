package org.zexnocs.teanekoagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.agent.access.*;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMTokenEstimate;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoagent.plugin.event.AgentEventContext;
import org.zexnocs.teanekoagent.plugin.event.AgentPromptPreparingEvent;
import org.zexnocs.teanekoagent.plugin.event.AgentToolsSelectingEvent;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.event.EventService;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.framework.composition.*;
import org.zexnocs.teanekocore.framework.function.MethodCallable;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;
import org.zexnocs.teanekoplugin.agent_example.ExampleClockFeature;
import org.zexnocs.teanekoplugin.agent_example.ExampleClockListener;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实 Agent、Core 管线及 LLM 交互验证多能力组合与迟到结果隔离。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
class AgentPluginIntegrationTest {
    /** 验证示例组件、System、资料环绕和工具定义完整接入，最终结果可以提交。 */
    @Test void installsFeatureThroughActualTurnPipeline() {
        try (var harness = new Harness()) {
            var execution = harness.service.run(harness.handle, harness.request()).toCompletableFuture(); harness.runtime.runAll();
            assertNotNull(harness.seen);
            harness.reply(); harness.runtime.runAll(); var result = execution.join();
            assertEquals(AgentRunResult.Status.SUCCESS, result.status());
            assertTrue(Objects.requireNonNull(result.promptTranscript()).input().plugins().containsKey(ExampleClockFeature.KEY));
            assertTrue(result.interaction().orElseThrow().tools().getFirst().output().contains("2026-11-02"));
            assertTrue(Objects.requireNonNull(result.promptTranscript()).input().fragments().stream().anyMatch(value -> value.text().contains("实时值由时间工具提供")));
            assertEquals("Asia/Shanghai", harness.handle.snapshot().require(ExampleClockFeature.ZONE));
        }
    }
    /** 验证模型等待期间禁用再启用插件后，旧候选不能成为成功结果。 */
    @Test void invalidatesTurnAfterPluginRevisionChanges() {
        try (var harness = new Harness()) {
            var execution = harness.service.run(harness.handle, harness.request()).toCompletableFuture(); harness.runtime.runAll();
            harness.plugins.setEnabled("example.clock", false); harness.plugins.setEnabled("example.clock", true);
            harness.reply(); harness.runtime.runAll(); assertNotEquals(AgentRunResult.Status.SUCCESS, execution.join().status());
        }
    }
    /** 验证可修改事件的拒绝不可撤销，宿主冻结后拒绝迟到写入。 */
    @Test void freezesDecisionAndPreservesVeto() {
        var context = new AgentEventContext(UUID.randomUUID(), org.zexnocs.teanekocore.framework.composition.EntityId.random(), "测试",
                new AgentScope("测试客户端", "群组"), Set.of(ExampleClockFeature.KEY));
        var event = new AgentPromptPreparingEvent(context, List.of()); event.setCancelled(true); event.setCancelled(false);
        assertTrue(event.isCancelled()); event.freeze(); assertThrows(IllegalStateException.class, () -> event.replace(List.of()));
    }
    /** 验证原事件总线拒绝会越过等待边界的异步决策监听器，模型不会启动。 */
    @Test void rejectsAsyncDecisionListener() {
        try (var harness = new Harness(new AsyncListener())) {
            var result = harness.service.run(harness.handle, harness.request()).toCompletableFuture(); harness.runtime.runAll();
            assertNotEquals(AgentRunResult.Status.SUCCESS, result.join().status()); assertNull(harness.seen);
        }
    }
    /** 验证交互后的插件拒绝不会让已经观察到的模型成功绕过最终提交检查。 */
    @Test void reviewStopCannotBecomeSuccessfulDelivery() {
        AgentFeature stopper = new AgentFeature() {
            /** @return 所属插件 */
            @Override public String pluginId() { return "example.clock"; }
            /** @return 校验能力 */
            @Override public String id() { return "stopper"; }
            /** @return 安装测试校验能力 */
            @Override public boolean defaultEnabled() { return true; }
            /** @return 交互后明确拒绝的步骤 */
            @Override public List<SystemContribution> systems() {
                return List.of(new SystemContribution(Phase.AFTER_INTERACTION, new IEntitySystem() {
                    /** @return 不写组件的明确步骤声明 */
                    @Override public SystemDescriptor descriptor() { return new SystemDescriptor("plugin.example.clock.stopper.stop", Set.of(), Set.of(), Set.of()); }
                    /**
                     * 拒绝交付而不伪造新的模型结果。
                     * @param context 原上下文
                     * @return 停止决定
                     */
                    @Override public CompletionStage<SystemResult> execute(SystemContext context) { return CompletableFuture.completedStage(SystemResult.stop(ComponentPatch.empty())); }
                }));
            }
        };
        try (var harness = new Harness(new ExampleClockListener(), stopper)) {
            var result = harness.service.run(harness.handle, harness.request()).toCompletableFuture(); harness.runtime.runAll(); harness.reply(); harness.runtime.runAll();
            assertEquals(AgentRunResult.Status.SKIPPED, result.join().status()); assertTrue(result.join().committableTranscript().isEmpty());
        }
    }

    /**
     * 故意违反决策等待合同的插件监听器，用于验证实际总线防护。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    @org.zexnocs.teanekocore.event.core.EventListener @PluginOwned("example.clock")
    public static final class AsyncListener {
        /**
         * 异步监听不应获得决策执行机会。
         * @param event 决策事件
         */
        @EventHandler(async = true) public void preparing(AgentPromptPreparingEvent event) { fail("异步决策监听器不应执行。"); }
    }

    /**
     * 复用可控交互执行器，不启动 Spring、数据库或真实模型。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    private static final class Harness implements AutoCloseable {
        final InteractionRuntime runtime = new InteractionRuntime();
        final PluginRegistry plugins = new PluginRegistry(List.of(() -> new PluginDescriptor("example.clock", "1", 1, Set.of())));
        final AgentRegistry registry = new AgentRegistry();
        final CompletableFuture<ILLMResult> pending = new CompletableFuture<>();
        final AgentService service;
        final AgentHandle handle;
        ILLMPrompt seen;
        /** 构造真实组合及独立轮次执行器，不读取外部配置。 */
        Harness() { this(new ExampleClockListener()); }
        /**
         * 装配实际事件扫描与总线，任务执行器仅省略线程调度。
         * @param listener 本轮安装的实际监听器
         */
        Harness(Object listener) { this(listener, null); }
        /**
         * 可选增加一个校验能力，复用相同完整业务夹具。
         * @param listener 实际监听器
         * @param additional 可选能力
         */
        @SuppressWarnings({"unchecked"})
        Harness(Object listener, AgentFeature additional) {
            var logger = mock(ILogger.class); var models = mock(ILLMModelService.class);
            var feature = new ExampleClockFeature(Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC));
            var loader = new DefaultListableBeanFactory();
            var beanScanner = mock(IBeanScanner.class); var classScanner = mock(IClassScanner.class);
            when(beanScanner.getBeansWithAnnotation(org.zexnocs.teanekocore.event.core.EventListener.class)).thenReturn(Map.of("listener",
                    new IndependentPair<>(listener.getClass().getAnnotation(org.zexnocs.teanekocore.event.core.EventListener.class), listener)));
            doReturn(Set.of(AgentPromptPreparingEvent.class, AgentToolsSelectingEvent.class)).when(classScanner).getClassesWithInterface(IEvent.class);
            var handlers = new EventHandlerScanner(beanScanner, logger, classScanner); handlers.init();
            var tasks = mock(ITaskService.class);
            when(tasks.subscribeWithFuture(anyString(), anyString(), any(MethodCallable.class), eq(Void.class))).thenAnswer(call -> {
                try { return TaskFuture.fromStage(logger, "插件事件", CompletableFuture.completedStage(call.<MethodCallable<ITaskResult<Void>>>getArgument(2).call())); }
                catch (Exception failure) { return TaskFuture.fromStage(logger, "插件事件", CompletableFuture.failedStage(failure)); }
            });
            var eventService = new EventService(logger, handlers, tasks); eventService.installPluginAccess(new PluginAccess(plugins));
            loader.registerSingleton("events", eventService);
            var strict = mock(IAgentPromptSource.class);
            when(strict.resolve(any(), any(), any(), any())).thenReturn(CompletableFuture.completedStage(List.of(new AgentPromptFragment("strict_source", "独立来源证据"))));
            when(strict.verify(any(), any(), any())).thenAnswer(call -> {
                var input = call.<AgentPromptInput>getArgument(1);
                assertEquals(List.of("strict_source"), input.fragments().stream().map(AgentPromptFragment::name).toList());
                return CompletableFuture.completedStage(null);
            });
            var other = new SourceAgentFeature("example.clock", "strict", true, (definition, request, settings) -> List.of(), strict, Set.of("strict_source"));
            var installed = new ArrayList<>(List.of(feature, other)); if (additional != null) { installed.add(additional); }
            var extensions = new AgentPluginRuntime(plugins, installed, loader.getBeanProvider(IEventService.class));
            handle = new AgentFactory(registry, extensions).create(new AgentDefinition("插件测试", new LLMModelOptionsSnapshot(null),
                    Set.of(AgentActivity.SERVICE, AgentActivity.TOOL), Set.of(ExampleClockFeature.KEY)));
            var snapshot = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("test").build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var readiness = mock(IAgentReadiness.class); when(readiness.inspect(any())).thenReturn(new AgentReadiness(AgentReadiness.Status.READY, Optional.of(snapshot)));
            var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE), readiness, logger);
            when(models.resolveOptions(any())).thenAnswer(call -> LLMModelOptions.copyOf(call.<ILLMPrompt>getArgument(0).getOptions()));
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                if (seen == null) { seen = call.getArgument(0); return new TaskFuture<>(logger, "插件测试模型", pending); }
                return TaskFuture.fromStage(logger, "插件测试回答", CompletableFuture.completedStage(answer()));
            });
            var interaction = new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler, ignored -> new LLMTokenEstimate(5, "test"));
            service = new AgentService(gate, interaction, extensions.tools((definition, request, settings) -> List.of()), new ObjectMapper(),
                    runtime, runtime.scheduler, runtime.clock, logger, null, extensions.prompts(IAgentPromptSource.empty()));
            service.installPlugins(extensions);
        }
        /** @return 具有真实用户来源的固定测试请求 */
        AgentRunRequest request() {
            var actor = new AgentActorContext(new AgentScope("test", "group"), AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "user", "source", "group");
            return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                    List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("查询时间").build()).build()));
        }
        /** 提交完整模型候选，让原交互及历史投影完成。 */
        void reply() {
            var assistant = LLMAssistantMessage.builder().toolCalls(List.of(LLMToolCall.builder().id("clock-call").name("example_clock_now").arguments("{}").build()))
                    .contents(new LLMContentListBuilder().build()).build();
            pending.complete(LLMResult.builder().usage(LLMUsage.builder().totalTokens(10).build())
                    .choices(List.of(LLMChoice.builder().index(0).message(assistant).finishReason("tool_calls").build())).build());
        }
        /** @return 工具完成后的最终模型回答 */
        private static LLMResult answer() {
            return LLMResult.builder().usage(LLMUsage.builder().totalTokens(10).build()).choices(List.of(LLMChoice.builder().index(0).finishReason("stop")
                    .message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("当前时间已确认。").build()).build()).build())).build();
        }
        /** 释放本用例拥有的轮次与实例，不关闭借用调度器。 */
        @Override public void close() { service.close(); registry.close(); runtime.runAll(); }
    }
}
