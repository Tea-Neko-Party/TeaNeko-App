package org.zexnocs.teanekoagent.runtime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.component.IAgentComponentPromptSource;
import org.zexnocs.teanekoagent.component.llm.framework.LLMOperationRejectedException;
import org.zexnocs.teanekoagent.component.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.component.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.*;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.component.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.component.llm.framework.tool.*;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.IContextualToolExecutor;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.component.llm.framework.tool.parameter.AbstractLLMFunctionParameter;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.configuration.AgentExecutionConfig;
import org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig;
import org.zexnocs.teanekoagent.integration.monitor.AgentObservationService;
import org.zexnocs.teanekoagent.runtime.access.*;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationKey;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.runtime.monitor.AgentActivityTimeline;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMCallLimits.MODEL_REQUESTS;
import static org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMCallLimits.TOTAL_TOKENS;
import static org.zexnocs.teanekoagent.runtime.AgentRunResult.Status.*;

/**
 * 使用真实 Core 管线和 LLM 交互服务验证 Agent 轮次、动态门控、可信上下文与资源拥有关系。
 *
 * <br>2026/11/02：验证管线失败记录可关联的安全诊断，日志失败仍不改变终态。
 * <br>2026/11/02：验证 client 工具白名单经过环绕后仍控制可见和实际执行。
 * <br>2026/11/03：复用冷轮次用例验证实际活动节点，不把模型结果当作平台发送。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证跨轮次完整输出额度与显式共享预算拒绝。
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 * <br>2026/11/04：验证上下文配置错误的根因诊断及秘密脱敏。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：验证上下文首个失败原因在结果与日志中保留。
 * <br>2026/11/06：验证真实 Agent 管线只输出一条汇总，内部 LLM 不重复输出。
 * <br>2026/11/06：覆盖前置拒绝、取消和失败的唯一终态通知，观察故障不改变结果或资源释放。
 * <br>2026/11/06：组件资料与宿主资料同时存在时只选择独立宿主来源，不自动执行未装配组件。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.6-l
 */
@Timeout(10)
class AgentServiceTest {
    /** 2026/11/04：无人格的记忆与表达组件按真实阶段执行，原历史端口不能被重复调用。 */
    @Test
    void memoryAndExpressionSystemsRunWithoutPersonalityComponent() {
        var assembly = org.zexnocs.teanekoagent.component.AgentAssembly.parse(
                "{\"version\":1,\"components\":{\"llm\":{},\"memory\":{},\"expression\":{}}}");
        policy.set(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE, "", assembly));
        var references = mock(org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences.class);
        when(references.resolve(any())).thenAnswer(call -> CompletableFuture.completedStage(
                new org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference(call.getArgument(0),
                        org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId.SHARED, 0, 0)));
        when(references.verify(any())).thenReturn(CompletableFuture.completedStage(null));
        var personalities = mock(org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource.class);
        when(personalities.verify(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
        var history = mock(org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder.class);
        when(history.record(any(), any(), nullable(org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage.class), any()))
                .thenReturn(CompletableFuture.completedStage(null));
        var expression = mock(org.zexnocs.teanekoagent.component.expression.ExpressionRuntime.class);
        when(expression.compile(any(), any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("history", history);
        beans.registerSingleton("expression", expression);
        var memory = new org.zexnocs.teanekoagent.component.memory.MemoryComponent(
                new org.zexnocs.teanekoagent.component.memory.MemoryInputSystem(beans.getBeanProvider(
                        org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder.class)),
                new org.zexnocs.teanekoagent.component.memory.MemoryOutputSystem(beans.getBeanProvider(
                        org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder.class)));
        var expressive = new org.zexnocs.teanekoagent.component.expression.ExpressionComponent(
                new org.zexnocs.teanekoagent.component.expression.ExpressionCompileSystem(beans.getBeanProvider(
                        org.zexnocs.teanekoagent.component.expression.ExpressionRuntime.class)));
        var llm = new org.zexnocs.teanekoagent.component.llm.LLMComponent(
                new org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser());
        var contributions = new ArrayList<org.zexnocs.teanekoagent.plugin.AgentFeature>();
        Map.of("llm", llm, "memory", memory, "expression", expressive).forEach((id, component) -> contributions.add(
                new org.zexnocs.teanekoagent.component.AgentComponentFeature("teaneko.agent", id, component)));
        for (String id : List.of("builtin-memory", "long-memory", "layered-memory", "expression")) {
            contributions.add(new org.zexnocs.teanekoagent.plugin.SourceAgentFeature("teaneko.agent", id, false,
                    (definition, request, runtime) -> List.of(), org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty()));
        }
        var plugins = new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(() ->
                new org.zexnocs.teanekocore.plugin.PluginDescriptor("teaneko.agent", "1", 1, Set.of())));
        try (var composed = new AgentService(gate, interactions, tools, new ObjectMapper(), runtime,
                runtime.scheduler, runtime.clock, logger, references,
                org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty(), personalities, history)) {
            composed.installPlugins(new org.zexnocs.teanekoagent.plugin.AgentPluginRuntime(plugins, contributions,
                    beans.getBeanProvider(org.zexnocs.teanekocore.event.interfaces.IEventService.class)));
            var result = composed.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture();
            runtime.runAll();
            reply(response());
            assertEquals(SUCCESS, result.join().status());
            verify(history).record(any(), any(), isNull(), any());
            verify(history).record(any(), any(), notNull(), any());
            verify(expression).compile(any(), any(), any(), any());
            verify(personalities, never()).resolve(any(), any());
        }
    }

    /** 2026/11/04：纯 LLM 装配走真实轮次，不能调用未安装的人格、记忆或工具端口。 */
    @Test
    void minimalAssemblyDoesNotInvokeOptionalCapabilities() {
        var assembly = org.zexnocs.teanekoagent.component.AgentAssembly.parse(
                org.zexnocs.teanekoagent.component.AgentAssembly.MINIMAL);
        policy.set(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE, "", assembly));
        var references = mock(org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences.class);
        var personalities = mock(org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource.class);
        var history = mock(org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder.class);
        var llm = new org.zexnocs.teanekoagent.component.llm.LLMComponent(
                new org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser());
        var plugins = new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(() ->
                new org.zexnocs.teanekocore.plugin.PluginDescriptor("teaneko.agent", "1", 1, Set.of())));
        var pluginRuntime = new org.zexnocs.teanekoagent.plugin.AgentPluginRuntime(plugins,
                List.of(new org.zexnocs.teanekoagent.component.AgentComponentFeature("teaneko.agent", "llm", llm)),
                new org.springframework.beans.factory.support.DefaultListableBeanFactory()
                        .getBeanProvider(org.zexnocs.teanekocore.event.interfaces.IEventService.class));
        try (var minimal = new AgentService(gate, interactions, tools, new ObjectMapper(), runtime,
                runtime.scheduler, runtime.clock, logger, references,
                org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty(), personalities, history)) {
            minimal.installPlugins(pluginRuntime);
            var result = minimal.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture();
            runtime.runAll();
            assertEquals(1, prompts.size());
            reply(response());
            assertEquals(SUCCESS, result.join().status());
            verifyNoInteractions(references, personalities, history, tools);
        }
    }

    /** 2026/11/06：冷轮次取消只汇总一次，不读取业务配置；迟到启动不能重复通知。 */
    @Test void coldExecutionCanBeCancelledBeforeAnyRuntimeWork() {
        installObservation(service, new AgentObservationFileConfig());
        var request = request(AgentActivity.SERVICE);
        var execution = service.prepare(agent(), request);
        verifyNoInteractions(scopes, readiness, tools, models);
        assertTrue(execution.cancel()); assertEquals(CANCELLED, execution.result().toCompletableFuture().join().status());
        execution.start(); runtime.runAll(); verifyNoInteractions(scopes, readiness, tools, models);
        assertSummary(request, CANCELLED, 0);
    }

    /** 2026/11/06：重复启动只执行一次；真实 Agent 管线汇总一次，并抑制内部 LLM 汇总。 */
    @Test void coldExecutionStartsOnceAndRecognizesActualModelControl() {
        var timeline = installObservation(service, new AgentObservationFileConfig());
        var request = request(AgentActivity.SERVICE);
        var execution = service.prepare(agent(), request);
        execution.start(); execution.start(); runtime.runAll(); assertEquals(1, prompts.size());
        assertTrue(execution.contains(modelPolicies.getFirst().control()));
        try (var unrelated = ExecutionControl.create()) { assertFalse(execution.contains(unrelated)); }
        reply(response()); assertEquals(SUCCESS, execution.result().toCompletableFuture().join().status()); assertFalse(execution.cancel());
        var nodes = timeline.page(request.actor().scope(), 0, 100).events();
        assertTrue(nodes.stream().anyMatch(node -> node.stage().equals("agent.prompt") && node.state().equals("STARTED")));
        assertEquals("SUCCESS", nodes.getLast().state());
        assertTrue(nodes.stream().noneMatch(node -> node.state().equals("SENT")));
        verify(logger, times(1)).info(eq("Agent 请求汇总"), contains("模型调用=1"));
        verify(logger, never()).info(eq("LLM 请求汇总"), anyString());
    }

    /** 2026/11/06：服务关闭回收冷轮次并发布一次取消终态，不遗留实例关闭监听。 */
    @Test void serviceCloseCompletesColdExecution() {
        installObservation(service, new AgentObservationFileConfig());
        var request = request(AgentActivity.SERVICE);
        var execution = service.prepare(agent(), request); service.close();
        assertEquals(CANCELLED, execution.result().toCompletableFuture().join().status());
        execution.start(); runtime.runAll(); verifyNoInteractions(scopes, readiness, tools, models);
        assertSummary(request, CANCELLED, 0);
    }

    /**
     * 2026/11/06：不支持的活动在准备阶段拒绝，不启动模型或配置查询，仍报告唯一终态。
     * @param activity 不属于独立轮次的活动
     */
    @ParameterizedTest
    @EnumSource(value = AgentActivity.class, names = {"COLLECT", "TOOL"})
    void unsupportedActivityStillReportsTerminalResult(AgentActivity activity) {
        installObservation(service, new AgentObservationFileConfig());
        var request = request(activity);
        var execution = service.prepare(agent(), request);
        assertEquals(INVALID_INPUT, execution.result().toCompletableFuture().join().status());
        execution.start();
        execution.start();
        assertFalse(execution.cancel());
        runtime.runAll();
        verifyNoInteractions(scopes, readiness, tools, models);
        assertSummary(request, INVALID_INPUT, 0);
    }

    /** 2026/11/06：服务已关闭后接受到的合法请求保留明确取消结果和一条汇总。 */
    @Test
    void closedServiceReportsTerminalResultWithoutBusinessWork() {
        installObservation(service, new AgentObservationFileConfig());
        service.close();
        var request = request(AgentActivity.SERVICE);
        assertEquals(CANCELLED, service.run(agent(), request).toCompletableFuture().join().status());
        runtime.runAll();
        verifyNoInteractions(scopes, readiness, tools, models);
        assertSummary(request, CANCELLED, 0);
    }

    /** 2026/11/06：无记忆端口却提供引用的配置错误不创建轮次，仍保留本次请求关联。 */
    @Test
    void unavailableMemoryPortReportsConfigurationRejection() {
        installObservation(service, new AgentObservationFileConfig());
        var original = request(AgentActivity.SERVICE);
        var request = original.withMemoryReference(new MemoryNamespaceReference(original.actor().scope(),
                MemoryNamespaceId.SHARED, 0, 0));
        assertEquals(CONFIGURATION_ERROR, service.run(agent(), request).toCompletableFuture().join().status());
        verifyNoInteractions(scopes, readiness, tools, models);
        assertSummary(request, CONFIGURATION_ERROR, 0);
    }

    /**
     * 2026/11/06：邮箱固定请求缺失会话或引用时发布配置拒绝，不尝试查询补全缺失信息。
     * @param missingConversation 是否缺会话，否则缺记忆引用
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void incompleteReferencedRequestReportsConfigurationRejection(boolean missingConversation) {
        var references = mock(IAgentMemoryReferences.class);
        try (var referenced = new AgentService(gate, interactions, tools, new ObjectMapper(), runtime,
                runtime.scheduler, runtime.clock, logger, references)) {
            installObservation(referenced, new AgentObservationFileConfig());
            var handle = agent();
            var original = request(AgentActivity.SERVICE);
            var scope = original.actor().scope();
            var request = new AgentRunRequest(original.requestId(), original.actor(), original.activity(), original.messages(),
                    missingConversation ? null : new AgentConversationStamp(new AgentConversationKey(handle.id(), scope), UUID.randomUUID()),
                    missingConversation ? new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0) : null);
            var execution = referenced.prepareReferenced(handle, request, null);
            assertEquals(CONFIGURATION_ERROR, execution.start().toCompletableFuture().join().status());
            execution.start();
            runtime.runAll();
            verifyNoInteractions(scopes, readiness, tools, models, references);
            assertSummary(request, CONFIGURATION_ERROR, 0);
        }
    }

    /** 2026/11/06：已停止父控制无法派生子轮次，仍报告取消；观察不得分配新的业务预算。 */
    @Test
    void stoppedParentReportsRejectionWithoutStartingChild() {
        installObservation(service, new AgentObservationFileConfig());
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            parent.cancel();
            var request = request(AgentActivity.SERVICE);
            assertEquals(CANCELLED, service.run(agent(), request, parent).toCompletableFuture().join().status());
            runtime.runAll();
            verifyNoInteractions(scopes, readiness, tools, models);
            assertSummary(request, CANCELLED, 0);
        }
    }

    /** 2026/11/06：运行取消和迟到模型结果不重复通知，也不反向关闭借用资源。 */
    @Test
    void runningCancellationReportsOnlyOnceAfterLateModelCompletion() {
        installObservation(service, new AgentObservationFileConfig());
        var request = request(AgentActivity.SERVICE);
        var execution = service.prepare(agent(), request);
        execution.start();
        runtime.runAll();
        assertEquals(1, replies.size());
        assertTrue(execution.cancel());
        assertEquals(CANCELLED, execution.result().toCompletableFuture().join().status());
        reply(response());
        execution.start();
        assertSummary(request, CANCELLED, 1);
    }

    /**
     * 2026/11/06：正文汇总开关同时控制提前返回与正常完成，内部模型不另发 INFO。
     * @param early 是否提前拒绝
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void disabledObservationSuppressesBothTerminalPaths(boolean early) {
        var settings = new AgentObservationFileConfig();
        settings.setAgentInfo(false);
        installObservation(service, settings);
        var result = service.run(agent(), request(early ? AgentActivity.COLLECT : AgentActivity.SERVICE));
        runtime.runAll();
        if (!early) {
            reply(response());
        }
        assertEquals(early ? INVALID_INPUT : SUCCESS, result.toCompletableFuture().join().status());
        verify(logger, never()).info(eq("Agent 请求汇总"), anyString());
        verify(logger, never()).info(eq("LLM 请求汇总"), anyString());
    }

    /**
     * 2026/11/06：宿主观察器和日志端口同时抛错，结果与计时器回收仍正确，且不会重试汇总。
     * @param early 是否提前拒绝
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void observationFailuresDoNotChangeBusinessResultOrRelease(boolean early) {
        var observed = new AtomicInteger();
        installObservation(service, new AgentObservationFileConfig());
        service.observe(report -> {
            observed.incrementAndGet();
            throw new IllegalStateException("宿主观察器故障");
        });
        doThrow(new IllegalStateException("日志端口故障")).when(logger).info(eq("Agent 请求汇总"), anyString());
        var request = request(early ? AgentActivity.COLLECT : AgentActivity.SERVICE);
        var result = service.run(agent(), request);
        runtime.runAll();
        if (!early) {
            reply(response());
        }
        assertEquals(early ? INVALID_INPUT : SUCCESS, result.toCompletableFuture().join().status());
        assertEquals(1, observed.get());
        assertEquals(0, runtime.pendingTimers());
        verify(logger, times(1)).info(eq("Agent 请求汇总"), contains(request.requestId().toString()));
    }

    /** 2026/11/06：请求编号不提供跨执行去重，两个合法的独立调用各有一次汇总。 */
    @Test
    void repeatedRequestIdDoesNotSuppressDistinctExecutions() {
        installObservation(service, new AgentObservationFileConfig());
        var request = request(AgentActivity.COLLECT);
        var handle = agent();
        service.run(handle, request).toCompletableFuture().join();
        service.run(handle, request).toCompletableFuture().join();
        verify(logger, times(2)).info(eq("Agent 请求汇总"), contains(request.requestId().toString()));
        verifyNoInteractions(scopes, readiness, tools, models);
    }

    /** 2026/11/06：空参数尚未形成有效执行，保持调用契约异常，不伪造请求汇总。 */
    @Test
    void invalidInvocationDoesNotInventTerminalRequest() {
        installObservation(service, new AgentObservationFileConfig());
        assertThrows(NullPointerException.class, () -> service.run(agent(), null));
        verify(logger, never()).info(eq("Agent 请求汇总"), anyString());
    }

    /**
     * 2026/11/06：复用真实观察器与原管线，文件配置由现有查询服务替身提供。
     * @param target 当前待观察服务
     * @param settings 本次可控观测配置
     * @return 可查询真实终态的时间线
     */
    private AgentActivityTimeline installObservation(AgentService target, AgentObservationFileConfig settings) {
        var files = mock(IFileConfigService.class);
        when(files.requireCurrent(AgentObservationFileConfig.class)).thenReturn(settings);
        var observation = new AgentObservationService(files, logger);
        var timeline = new AgentActivityTimeline();
        timeline.installObservation(observation);
        interactions.installObserver(observation);
        target.installTimeline(timeline);
        return timeline;
    }

    /**
     * 2026/11/06：检查真实日志次数与业务结果对应，并确认内部 LLM 不重复汇总。
     * @param request 原有效请求
     * @param status 实际终态
     * @param modelCalls 实际模型调用次数
     */
    private void assertSummary(AgentRunRequest request, AgentRunResult.Status status, int modelCalls) {
        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(eq("Agent 请求汇总"), message.capture());
        assertTrue(message.getValue().contains("请求=" + request.requestId()));
        assertTrue(message.getValue().contains("状态=" + status));
        assertTrue(message.getValue().contains("模型调用=" + modelCalls));
        verify(logger, never()).info(eq("LLM 请求汇总"), anyString());
    }
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final IAgentFactory factory = new AgentFactory(registry);
    private final ILogger logger = mock(ILogger.class);
    private final IAgentScopePolicyPort scopes = mock(IAgentScopePolicyPort.class);
    private final IAgentReadiness readiness = mock(IAgentReadiness.class);
    private final IAgentToolSource tools = mock(IAgentToolSource.class);
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final AgentAccessGate gate = new AgentAccessGate(registry, scopes, readiness, logger);
    private final AtomicReference<AgentScopePolicy> policy = new AtomicReference<>(
            new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE));
    private final AtomicReference<AgentRuntimeSnapshot> snapshot = new AtomicReference<>(snapshot(LLMInteractionSettings.defaults(), "冻结模型"));
    private final AtomicReference<AgentReadiness.Status> availability = new AtomicReference<>(AgentReadiness.Status.READY);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final List<ExecutionPolicy> modelPolicies = new ArrayList<>();
    private final AtomicInteger scopeReads = new AtomicInteger();
    private final LLMInteractionService interactions = new LLMInteractionService(models, LLMInteractionSettings::defaults,
            runtime, runtime.scheduler, prompt -> new LLMTokenEstimate(5, "fixed-v1"));
    private final AgentService service = create(interactions, runtime);

    /** 装配可控配置和未完成模型任务，不启动 Spring 或真实 HTTP。 */
    AgentServiceTest() {
        when(scopes.read(any())).thenAnswer(ignored -> { scopeReads.incrementAndGet(); return policy.get(); });
        when(readiness.inspect(any())).thenAnswer(ignored -> new AgentReadiness(availability.get(),
                availability.get() == AgentReadiness.Status.READY ? Optional.of(snapshot.get()) : Optional.empty()));
        when(tools.select(any(), any(), any())).thenReturn(List.of());
        doCallRealMethod().when(tools).select(any(), any(), any(), any());
        when(models.resolveOptions(any())).thenAnswer(invocation ->
                LLMModelOptions.copyOf(invocation.<ILLMPrompt>getArgument(0).getOptions()));
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0)); modelPolicies.add(invocation.getArgument(1));
            var reply = new CompletableFuture<ILLMResult>(); replies.add(reply);
            return new TaskFuture<>(logger, "Agent 离线模型", reply);
        });
    }

    /** 清理本用例拥有的服务和实例，借用计时器不得被服务关闭。 */
    @AfterEach void cleanup() {
        service.close(); registry.close(); runtime.runAll();
        assertEquals(0, runtime.pendingTimers());
        verify(runtime.scheduler, never()).shutdown();
        verify(runtime.scheduler, never()).shutdownNow();
    }

    /**
     * 构造可独立冻结的运行快照。
     * @param settings 本轮实际预算和时限
     * @param model 本轮具体模型
     * @return 无凭据的运行数据
     */
    private static AgentRuntimeSnapshot snapshot(LLMInteractionSettings settings, String model) {
        return new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test")
                .model(model).maxTokens(20).metadata(Map.of("vendor.state", List.of("固定值"))).build()),
                ModelCapabilities.textOnly(), settings);
    }

    /**
     * 注入替换端口或执行器，保留真正的 Agent 管线。
     * @param port 完整交互端口
     * @param executor 借用的业务执行器
     * @return 当前用例拥有的新服务
     */
    private AgentService create(ILLMInteractionService port, Executor executor) {
        return new AgentService(gate, port, tools, new ObjectMapper(), executor, runtime.scheduler, runtime.clock, logger);
    }

    /**
     * 创建独立实例，定义共享不产生共享运行状态。
     * @return 全能力测试实例
     */
    private AgentHandle agent() {
        return factory.create(new AgentDefinition("测试定义", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    }

    /**
     * 构造宿主可信身份，系统活动不会继承聊天用户身份。
     * @param activity 请求活动
     * @return 已冻结请求
     */
    private AgentRunRequest request(AgentActivity activity) {
        boolean system = activity == AgentActivity.SCHEDULED || activity == AgentActivity.PROACTIVE;
        var actor = new AgentActorContext(new AgentScope("客户端", "测试群"), system ? AgentActorContext.Origin.SYSTEM
                : AgentActorContext.Origin.USER, system ? Optional.empty() : Optional.of(UUID.randomUUID()),
                system ? "" : "平台用户", "消息来源", "固定目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, activity, List.of(LLMUserMessage.builder()
                .contents(new LLMContentListBuilder().addText("离线问题").build()).build()));
    }

    /**
     * 构造一个可识别配对的工具请求。
     * @param id 调用标识
     * @return 零参数工具请求
     */
    private LLMToolCall call(String id) { return LLMToolCall.builder().id(id).name("work").arguments("{}").build(); }

    /**
     * 提供明确终止原因及真实报告用量的模型响应。
     * @param calls 需要执行的工具列表，空表示最终回答
     * @return 统一响应
     */
    private LLMResult response(ILLMToolCall... calls) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build())
                .choices(List.of(LLMChoice.builder().index(0).finishReason(calls.length == 0 ? "stop" : "tool_calls")
                        .message(LLMAssistantMessage.builder().toolCalls(List.of(calls))
                                .contents(new LLMContentListBuilder().addText("模型回答").build()).build()).build())).build();
    }

    /**
     * 固定本次显式工具绑定。
     * @param action 受控工具实现
     */
    private void select(IContextualToolExecutor action) {
        when(tools.select(any(), any(), any())).thenReturn(List.of(new LLMToolBinding(
                LLMTool.builder().name("work").build(), action)));
    }

    /**
     * 完成当前供应商响应并推进所有业务阶段。
     * @param value 本次响应
     */
    private void reply(ILLMResult value) { replies.getLast().complete(value); runtime.runAll(); }

    /**
     * 先确认实际模型调用携带参数，再读取本地稳定引用，避免对可空覆盖项直接解引用。
     * @param prompt 已捕获的实际模型输入
     * @return 当前调用非空的模型参数
     * @throws AssertionError 当实际调用缺少参数时
     */
    private static ILLMModelOptions requireOptions(ILLMPrompt prompt) {
        var options = prompt.getOptions();
        assertNotNull(options, "实际模型调用必须携带已解析参数。");
        return options;
    }

    /** 验证实际完成等待、输入深层隔离，以及结果复核不会阻塞模型完成线程。 */
    @Test void waitsForModelAndDeliveryGateInBusinessExecutor() {
        var handle = agent();
        var original = LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("原文").build()).build();
        var base = request(AgentActivity.SERVICE);
        var request = new AgentRunRequest(base.requestId(), base.actor(), base.activity(), List.of(original));
        var result = service.run(handle, request).toCompletableFuture();
        ((TextLLMContentPart) original.getContents().getFirst().getContentPart()).setText("改写原文");
        verifyNoInteractions(scopes, readiness, tools, models);
        runtime.runAll();
        assertFalse(result.isDone());
        assertEquals("原文", ((TextLLMContentPart) prompts.getFirst().getMessages().getFirst().getContents().getFirst().getContentPart()).getText());
        var reads = scopeReads.get();
        replies.getFirst().complete(response());
        assertEquals(reads, scopeReads.get());
        assertFalse(result.isDone());
        runtime.runAll();
        var completed = result.join();
        assertEquals(SUCCESS, completed.status());
        assertEquals(handle.id(), completed.agentId()); assertEquals(request.requestId(), completed.requestId());
        assertTrue(completed.answer().isPresent()); assertEquals(2, completed.committableTranscript().orElseThrow().size());
        assertEquals(1, completed.budgetUsage().get(MODEL_REQUESTS).used());
        assertEquals(LLMOptionsPolicy.RESOLVED, prompts.getFirst().getOptionsPolicy());
        assertEquals(0, runtime.pendingTimers());
    }

    /** 2026/11/02：客户端限制经过插件环绕仍有效，未声明范围的工具保持通用。 */
    @Test void clientToolRestrictionsSurviveWrappingAndFilterActualModelTools() {
        var request = request(AgentActivity.COMMAND); var calls = new AtomicInteger();
        var allowed = new LLMToolBinding(LLMTool.builder().name("work").build(), (call, context) -> {
            calls.incrementAndGet(); return CompletableFuture.completedStage("完成");
        }).forClients(Set.of(request.actor().scope().clientId())).intercept((call, context, next) -> next.call(call, context));
        var hidden = new LLMToolBinding(LLMTool.builder().name("foreign").build(), (call, context) -> CompletableFuture.completedStage("不应调用"))
                .forClients(Set.of("其他客户端")).guarded((call, context) -> { });
        var common = new LLMToolBinding(LLMTool.builder().name("common").build(), (call, context) -> CompletableFuture.completedStage("通用"));
        assertFalse(hidden.supportsClient(request.actor().scope().clientId()));
        assertThrows(IllegalArgumentException.class, () -> hidden.forClients(Set.of()));
        when(tools.select(any(), any(), any())).thenReturn(List.of(allowed, hidden, common));
        var result = service.run(agent(), request).toCompletableFuture(); runtime.runAll();
        assertEquals(List.of("work", "common"), requireOptions(prompts.getFirst()).getTools().stream().map(ILLMTool::getName).toList());
        reply(response(call("受限工具"))); reply(response());
        assertEquals(SUCCESS, result.join().status()); assertEquals(1, calls.get());
    }

    /** 验证工具真正完成前不发下一模型请求，模型参数不能覆盖可信身份。 */
    @Test void toolStageUsesHostIdentityAndWaitsForRealCompletion() {
        var request = request(AgentActivity.COMMAND);
        var pendingTool = new CompletableFuture<String>();
        var seen = new AtomicReference<ToolInvocationContext>();
        var schema = AbstractLLMFunctionParameter.object(null, Map.of("identity",
                AbstractLLMFunctionParameter.primitive("string", "模型业务参数")), List.of());
        when(tools.select(any(), any(), any())).thenReturn(List.of(new LLMToolBinding(
                LLMTool.builder().name("work").parameters(schema).build(), (call, context) -> {
                    seen.set(context); return pendingTool;
                })));
        var pending = service.run(agent(), request).toCompletableFuture(); runtime.runAll();
        var call = call("可信调用"); call.setArguments("{\"identity\":\"伪造身份\"}");
        reply(response(call));
        assertFalse(pending.isDone()); assertEquals(1, prompts.size());
        assertEquals(request.requestId(), seen.get().requestId());
        assertEquals(request.actor(), seen.get().bindings().require(AgentComponents.ACTOR));
        assertEquals(request, seen.get().bindings().require(AgentComponents.REQUEST));
        assertEquals("固定目标", seen.get().bindings().require(AgentComponents.ACTOR).targetId());
        pendingTool.complete("真正业务完成"); runtime.runAll();
        assertEquals(2, prompts.size()); assertFalse(pending.isDone());
        reply(response());
        assertEquals(SUCCESS, pending.join().status());
        assertEquals(1, pending.join().interaction().orElseThrow().tools().size());
        assertEquals("真正业务完成", pending.join().interaction().orElseThrow().tools().getFirst().output());
    }

    /**
     * 验证所有模型轮次入口在未登记 scope 时不调用工具源或模型。
     * @param activity 不同的合法轮次活动
     */
    @ParameterizedTest @EnumSource(value = AgentActivity.class, names = {"SERVICE", "COMMAND", "AUTOMATIC_REPLY", "SUMMARY", "PROACTIVE", "SCHEDULED"})
    void unregisteredScopeHasNoActions(AgentActivity activity) {
        policy.set(AgentScopePolicy.unregistered());
        var pending = service.run(agent(), request(activity)); runtime.runAll();
        assertEquals(SKIPPED, pending.toCompletableFuture().join().status());
        assertEquals(AgentAccessDecision.Reason.SCOPE_UNREGISTERED, pending.toCompletableFuture().join().access().orElseThrow().reason());
        verifyNoInteractions(readiness, tools, models);
    }

    /**
     * 验证禁用 scope 时所有模型轮次入口均关闭。
     * @param activity 不同的合法轮次活动
     */
    @ParameterizedTest @EnumSource(value = AgentActivity.class, names = {"SERVICE", "COMMAND", "AUTOMATIC_REPLY", "SUMMARY", "PROACTIVE", "SCHEDULED"})
    void disabledScopeHasNoActions(AgentActivity activity) {
        policy.set(new AgentScopePolicy(true, false, AgentScopePolicy.Mode.ACTIVE));
        var pending = service.run(agent(), request(activity)); runtime.runAll();
        assertEquals(SKIPPED, pending.toCompletableFuture().join().status());
        verifyNoInteractions(readiness, tools, models);
    }

    /** 验证两个实例共享定义仍独立执行，关闭一个不会停止另一个或改写迟到结果。 */
    @Test void closingOneInstanceDoesNotCancelAnother() {
        var first = agent(); var second = factory.create(first.definition());
        var a = service.run(first, request(AgentActivity.SERVICE)).toCompletableFuture();
        var b = service.run(second, request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        assertEquals(2, prompts.size()); assertTrue(registry.remove(first.id()));
        var cancelled = a.join(); assertEquals(CANCELLED, cancelled.status()); assertFalse(b.isDone());
        assertEquals(1, cancelled.interaction().orElseThrow().modelAttempts());
        replies.getFirst().complete(response()); runtime.runAll(); assertSame(cancelled, a.join());
        reply(response()); assertEquals(SUCCESS, b.join().status());
        assertEquals(1, b.join().budgetUsage().get(MODEL_REQUESTS).used());
    }

    /** 验证派生轮次共享父总额，第二轮不能通过新局部设置重新获得额度。 */
    @Test void derivedRunsShareBudgetAndLeaveParentActive() {
        try (var parent = runtime.parent(new LLMCallLimits(1, 0, 0))) {
            var handle = agent();
            var first = service.run(handle, request(AgentActivity.SERVICE), parent).toCompletableFuture(); runtime.runAll(); reply(response());
            assertEquals(SUCCESS, first.join().status());
            assertTrue(requireOptions(prompts.getFirst()).getTools().isEmpty());
            var second = service.run(handle, request(AgentActivity.SERVICE), parent).toCompletableFuture(); runtime.runAll();
            assertEquals(BUDGET_EXCEEDED, second.join().status()); assertEquals(1, replies.size());
            assertEquals(1, parent.budget().used(MODEL_REQUESTS)); assertDoesNotThrow(parent::checkActive);
        }
    }

    /** 验证排队准备期间取消立即完成，后续排队回调不能读配置或发起模型。 */
    @Test void cancellationBeforePreparationHasNoActions() {
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var result = service.run(agent(), request(AgentActivity.SERVICE), parent).toCompletableFuture();
            parent.cancel(); assertEquals(CANCELLED, result.join().status()); runtime.runAll();
            verifyNoInteractions(scopes, readiness, tools, models);
        }
    }

    /** 验证父控制已经过期时不构造新的总额度，也不读取任何配置。 */
    @Test void expiredParentRemainsTimedOut() {
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            runtime.advance(Duration.ofDays(1));
            var result = service.run(agent(), request(AgentActivity.SERVICE), parent).toCompletableFuture().join();
            assertEquals(TIMED_OUT, result.status()); verifyNoInteractions(scopes, readiness, tools, models);
        }
    }

    /** 验证独立轮次按实际就绪设置建立总截止时间，并保留被取消的模型尝试。 */
    @Test void independentDeadlineStopsActualInteraction() {
        var defaults = LLMInteractionSettings.defaults();
        snapshot.set(snapshot(new LLMInteractionSettings(defaults.limits(), Duration.ofSeconds(100),
                Duration.ofSeconds(100), Duration.ofSeconds(10)), "短时限模型"));
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        runtime.advance(Duration.ofSeconds(10));
        var ended = result.join(); assertEquals(TIMED_OUT, ended.status());
        assertEquals(1, ended.interaction().orElseThrow().modelAttempts()); assertTrue(ended.answer().isEmpty());
        replies.getFirst().complete(response()); runtime.runAll(); assertSame(ended, result.join());
    }

    /** 验证独立请求的准备排队耗时计入实际总时限，准备完成后不重新获得完整时长。 */
    @Test void preparationDelayConsumesIndependentTurnTime() {
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture();
        runtime.advance(Duration.ofSeconds(121)); runtime.runAll();
        assertEquals(TIMED_OUT, result.join().status()); verifyNoInteractions(tools, models);
    }

    /** 验证工具执行到一半关闭实例，已完成动作和模型 usage 保留，未完成动作不伪装回滚。 */
    @Test void closeDuringToolsRetainsObservedActionsAndUsage() {
        var handle = agent(); var waiting = new CompletableFuture<String>(); var effects = new ArrayList<String>();
        select((call, context) -> { effects.add(call.getId()); return effects.size() == 1
                ? CompletableFuture.completedStage("已完成动作") : waiting; });
        var result = service.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        reply(response(call("完成"), call("等待"))); assertEquals(List.of("完成", "等待"), effects);
        handle.close(); var ended = result.join(); assertEquals(CANCELLED, ended.status());
        var interaction = ended.interaction().orElseThrow();
        assertEquals(1, interaction.tools().size()); assertEquals(2, interaction.attempts().getFirst().usage().orElseThrow().getTotalTokens());
        waiting.complete("迟到完成"); runtime.runAll(); assertEquals(1, replies.size()); assertSame(ended, result.join());
        assertTrue(ended.committableTranscript().isEmpty());
    }

    /** 验证模型等待期间注销，模型已发生用量保留，但工具副作用和追加模型均被拒绝。 */
    @Test void unregisterBeforeToolPreventsEveryEffect() {
        var effects = new AtomicInteger(); select((call, context) -> {
            effects.incrementAndGet(); return CompletableFuture.completedStage("不应执行");
        });
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        policy.set(AgentScopePolicy.unregistered()); reply(response(call("a"), call("b")));
        assertEquals(SKIPPED, result.join().status()); assertEquals(0, effects.get()); assertEquals(1, replies.size());
        assertEquals(1, result.join().interaction().orElseThrow().modelAttempts()); assertTrue(result.join().answer().isEmpty());
    }

    /** 验证第一工具改变开关后，第二工具在真正执行器前被拒绝，余下批次停止。 */
    @Test void disableBetweenToolsStopsRemainder() {
        var effects = new ArrayList<String>(); select((call, context) -> {
            effects.add(call.getId()); policy.set(new AgentScopePolicy(true, false, AgentScopePolicy.Mode.ACTIVE));
            return CompletableFuture.completedStage("第一个动作已完成");
        });
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        reply(response(call("a"), call("b"), call("c")));
        assertEquals(List.of("a"), effects); assertEquals(SKIPPED, result.join().status()); assertEquals(1, replies.size());
        assertEquals(1, result.join().interaction().orElseThrow().tools().size());
    }

    /** 验证工具完成时注销，下一次模型准入守卫拒绝发出新请求，而非只在最终交付检查。 */
    @Test void unregisterAfterToolBlocksNextModel() {
        var waiting = new CompletableFuture<String>(); select((call, context) -> waiting);
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        reply(response(call("a"))); policy.set(AgentScopePolicy.unregistered()); waiting.complete("已完成"); runtime.runAll();
        assertEquals(SKIPPED, result.join().status()); assertEquals(1, replies.size());
        assertEquals(1, result.join().interaction().orElseThrow().tools().size());
    }

    /** 验证最终回答已产生但提交前注销时，只保留诊断，不公开回答或正常历史。 */
    @Test void unregisterBeforeDeliverySuppressesSuccessfulCandidate() {
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response()); policy.set(AgentScopePolicy.unregistered()); runtime.runAll();
        assertEquals(SKIPPED, result.join().status()); assertTrue(result.join().answer().isEmpty());
        assertTrue(result.join().committableTranscript().isEmpty());
        assertEquals(LLMInteractionResult.Status.SUCCESS, result.join().interaction().orElseThrow().status());
    }

    /** 验证 readiness 热更新只影响新轮次，不改变同轮参数和总预算。 */
    @Test void readinessReloadUsesPinnedRuntimeUntilNextRun() {
        select((call, context) -> {
            snapshot.set(snapshot(new LLMInteractionSettings(new LLMCallLimits(1, 0, 0), Duration.ofSeconds(1),
                    Duration.ofSeconds(1), Duration.ofSeconds(1)), "新模型"));
            return CompletableFuture.completedStage("配置已更新");
        });
        var handle = agent(); var result = service.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        reply(response(call("a"))); reply(response()); assertEquals(SUCCESS, result.join().status());
        assertEquals(List.of("冻结模型", "冻结模型"), prompts.stream().map(prompt -> requireOptions(prompt).getModel()).toList());
        assertEquals(3, result.join().budgetUsage().get(MODEL_REQUESTS).limit());
        var next = service.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        assertEquals("新模型", requireOptions(prompts.getLast()).getModel()); reply(response()); assertEquals(SUCCESS, next.join().status());
    }

    /** 验证配置不可用只影响当前请求，修复后下一次执行可正常工作。 */
    @Test void unavailableReadinessCanRecover() {
        availability.set(AgentReadiness.Status.CREDENTIALS_UNAVAILABLE);
        var first = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        assertEquals(CONFIGURATION_ERROR, first.join().status()); verifyNoInteractions(tools, models);
        availability.set(AgentReadiness.Status.READY);
        var second = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll(); reply(response());
        assertEquals(SUCCESS, second.join().status());
    }

    /** 验证没有工具能力的定义连工具源都不读取，保持普通无工具执行可用。 */
    @Test void definitionWithoutToolActivityDoesNotExposeTools() {
        var handle = factory.create(new AgentDefinition("纯模型", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.SERVICE)));
        var result = service.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll(); reply(response());
        assertEquals(SUCCESS, result.join().status()); assertTrue(requireOptions(prompts.getFirst()).getTools().isEmpty());
        verifyNoInteractions(tools);
    }

    /** 验证采集和工具执行类别不能被误用成主动调用模型的轮次。 */
    @Test void nonModelActivitiesDoNotInvokeModel() {
        for (var activity : List.of(AgentActivity.COLLECT, AgentActivity.TOOL)) {
            assertEquals(INVALID_INPUT, service.run(agent(), request(activity)).toCompletableFuture().join().status());
        }
        verifyNoInteractions(scopes, readiness, tools, models);
    }

    /** 2026/11/02：验证工具源失败的类型诊断及正文脱敏，日志故障不丢失结果或资源。 */
    @Test void selectionFailureIsRedactedAndReleasesResources() {
        when(tools.select(any(), any(), any())).thenThrow(new IllegalStateException("不可导出的敏感异常"));
        doThrow(new IllegalStateException("日志失败")).when(logger).error(anyString(), anyString());
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        assertEquals(FAILED, result.join().status()); assertFalse(result.join().toString().contains("不可导出的敏感异常"));
        assertEquals(0, runtime.pendingTimers()); verifyNoInteractions(models);
        var diagnostic = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger).error(eq(AgentService.class.getName()), diagnostic.capture());
        assertTrue(diagnostic.getValue().contains("异常类型="));
        assertTrue(diagnostic.getValue().contains("IllegalStateException"));
        assertFalse(diagnostic.getValue().contains("不可导出的敏感异常"));
    }

    /** 2026/11/04：被转换为配置错误的上下文失败保留安全类型和位置，禁止输出原异常正文。 */
    @Test
    void rejectedContextRetainsSafeDiagnostic() {
        var source = mock(org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.class);
        // 2026/11/04：实际存在资料才进入来源复核；完成性断言避免手动执行器夹具无限等待。
        var fragment = new org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment("test_resource", "待复核的资料");
        when(source.resolve(any(), any(), any(), any())).thenReturn(CompletableFuture.completedStage(List.of(fragment)));
        when(source.verify(any(), any(), any())).thenReturn(CompletableFuture.failedStage(
                new IllegalArgumentException("上下文中的秘密不能写入日志")));
        try (var custom = new AgentService(gate, interactions, tools, new ObjectMapper(), runtime,
                runtime.scheduler, runtime.clock, logger, null, source, null)) {
            var result = custom.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture();
            runtime.runAll();
            assertTrue(result.isDone());
            assertEquals(CONFIGURATION_ERROR, result.join().status());
            // 2026/11/05：结果与日志都保留首个上下文原因，不被随后的停止状态覆盖。
            assertTrue(java.util.Objects.requireNonNull(result.join().failure()).types()
                    .contains(IllegalArgumentException.class.getName()));
            var diagnostic = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(logger).error(eq(AgentService.class.getName()), diagnostic.capture());
            assertTrue(diagnostic.getValue().contains("IllegalArgumentException"));
            assertFalse(diagnostic.getValue().contains("上下文中的秘密"));
            verifyNoInteractions(models);
        }
    }

    /** 验证业务执行器拒绝排队时 Stage 仍以业务失败结束。 */
    @Test void rejectedExecutorCompletesResult() {
        try (var rejected = create(interactions, ignored -> { throw new RejectedExecutionException("测试拒绝"); })) {
            assertEquals(FAILED, rejected.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture().join().status());
            verifyNoInteractions(scopes, readiness, tools, models);
        }
    }

    /** 验证不能支持动态守卫的自定义交互端口明确不可用，不能静默跳过授权。 */
    @Test void unadaptedInteractionPortFailsClosed() {
        var unsupported = mock(ILLMInteractionService.class, CALLS_REAL_METHODS);
        try (var custom = create(unsupported, runtime)) {
            var result = custom.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
            assertEquals(CONFIGURATION_ERROR, result.join().status()); verifyNoInteractions(models);
        }
    }

    /** 验证不响应取消的异步端口不会拖住本轮，迟到成功不能提交。 */
    @Test void unresponsiveAsyncPortCannotBlockCancellation() {
        var port = mock(ILLMInteractionService.class);
        var pending = new CompletableFuture<LLMInteractionResult>();
        when(port.interact(any(), any(), any(), any(), any())).thenReturn(pending);
        try (var custom = create(port, runtime)) {
            var handle = agent(); var result = custom.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
            handle.close(); var ended = result.join(); assertEquals(CANCELLED, ended.status());
            assertTrue(ended.interaction().isEmpty()); pending.complete(new LLMInteractionResult(LLMInteractionResult.Status.FAILED,
                    List.of(), null, List.of(), List.of(), Map.of())); runtime.runAll(); assertSame(ended, result.join());
        }
    }

    /** 验证取消只读结果视图不会取消根轮次，主动停止由宿主控制或实例关闭触发。 */
    @Test void cancellingResultViewDoesNotCancelExecution() {
        var stage = service.run(agent(), request(AgentActivity.SERVICE));
        var view = stage.toCompletableFuture(); assertTrue(view.cancel(false)); runtime.runAll(); reply(response());
        assertEquals(SUCCESS, stage.toCompletableFuture().join().status());
    }

    /** 验证服务关闭取消自己拥有的轮次，保留实例、父预算和共享调度器。 */
    @Test void serviceCloseLeavesHandlesAndParentUsable() {
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var handle = agent(); var result = service.run(handle, request(AgentActivity.SERVICE), parent).toCompletableFuture(); runtime.runAll();
            service.close(); service.close(); assertEquals(CANCELLED, result.join().status());
            assertFalse(handle.isClosed()); assertDoesNotThrow(parent::checkActive);
            assertEquals(CANCELLED, service.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture().join().status());
        }
    }

    /** 验证 token 预检期间注销，动态模型守卫在预留和实际调用前拒绝。 */
    @Test void modelGuardRechecksAfterTokenPreparation() {
        var defaults = LLMInteractionSettings.defaults();
        snapshot.set(snapshot(new LLMInteractionSettings(new LLMCallLimits(3, 2, 6, 30L), defaults.modelTimeout(),
                defaults.toolTimeout(), defaults.turnTimeout()), "预算模型"));
        doAnswer(invocation -> {
            policy.set(AgentScopePolicy.unregistered());
            return LLMModelOptions.copyOf(invocation.<ILLMPrompt>getArgument(0).getOptions());
        }).when(models).resolveOptions(any());
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        assertEquals(SKIPPED, result.join().status()); assertTrue(replies.isEmpty());
        assertEquals(0, result.join().budgetUsage().get(MODEL_REQUESTS).used());
        assertEquals(0, result.join().budgetUsage().get(TOTAL_TOKENS).used());
    }

    /** 2026/11/04：共享预算沿派生轮次保留，完整输出不能容纳时拒绝后续请求。 */
    @Test void inheritedTokenBudgetIsNotResetAcrossRuns() {
        try (var parent = runtime.parent(new LLMCallLimits(3, 2, 6, 27L))) {
            var handle = agent(); var first = service.run(handle, request(AgentActivity.SERVICE), parent).toCompletableFuture();
            runtime.runAll(); assertEquals(20, requireOptions(prompts.getFirst()).getMaxTokens()); reply(response());
            assertEquals(SUCCESS, first.join().status());
            var second = service.run(handle, request(AgentActivity.SERVICE), parent).toCompletableFuture(); runtime.runAll();
            assertEquals(20, requireOptions(prompts.getLast()).getMaxTokens()); reply(response());
            assertEquals(SUCCESS, second.join().status()); assertEquals(4, parent.budget().used(TOTAL_TOKENS));
            var third = service.run(handle, request(AgentActivity.SERVICE), parent).toCompletableFuture();
            runtime.runAll();
            assertEquals(org.zexnocs.teanekoagent.runtime.AgentRunResult.Status.BUDGET_EXCEEDED, third.join().status());
            assertEquals(2, prompts.size());
            assertEquals(4, parent.budget().used(TOTAL_TOKENS));
            assertDoesNotThrow(parent::checkActive);
        }
    }

    /** 验证宿主未声明所需维度时明确拒绝，不用新预算替换调用方控制。 */
    @Test void missingParentBudgetDimensionsCannotCreateFreshAllowance() {
        try (var parent = ExecutionControl.create()) {
            var result = service.run(agent(), request(AgentActivity.SERVICE), parent).toCompletableFuture(); runtime.runAll();
            assertEquals(INVALID_INPUT, result.join().status()); assertTrue(replies.isEmpty());
            assertTrue(parent.budget().snapshot().isEmpty()); assertDoesNotThrow(parent::checkActive);
        }
    }

    /** 验证准备开始前已关闭的实例无需读取配置，迟到排队工作同样没有动作。 */
    @Test void closedInstanceBeforeStartHasNoActions() {
        var handle = agent(); handle.close();
        var result = service.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture();
        assertEquals(CANCELLED, result.join().status()); runtime.runAll();
        verifyNoInteractions(scopes, readiness, tools, models);
    }

    /** 2026/11/06：模型异步失败保留实际尝试且只汇总一次，不导出原始异常正文。 */
    @Test void modelFailureRetainsAttemptWithoutExposingException() {
        installObservation(service, new AgentObservationFileConfig());
        var request = request(AgentActivity.SERVICE);
        var result = service.run(agent(), request).toCompletableFuture(); runtime.runAll();
        replies.getFirst().completeExceptionally(new IllegalStateException("供应商敏感诊断")); runtime.runAll();
        var ended = result.join(); assertEquals(FAILED, ended.status());
        assertEquals(1, ended.interaction().orElseThrow().modelAttempts());
        assertFalse(ended.toString().contains("供应商敏感诊断")); assertTrue(ended.answer().isEmpty());
        assertSummary(request, FAILED, 1);
    }

    /** 验证计时器拒绝登记时也能结束请求，不能遗留半初始化控制。 */
    @Test void rejectedSchedulerCompletesAndReleasesControl() {
        doThrow(new RejectedExecutionException("离线计时器拒绝")).when(runtime.scheduler)
                .schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), any(TimeUnit.class));
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        assertEquals(FAILED, result.join().status()); verifyNoInteractions(models);
    }

    /** 验证同一活动仍被新模式允许时，也不能把改变后的策略混入当前轮次。 */
    @Test void scopePolicyChangeRequiresNewRun() {
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        policy.set(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.PASSIVE_BASIC)); reply(response());
        assertEquals(SKIPPED, result.join().status());
        assertEquals(AgentAccessDecision.Reason.SCOPE_POLICY_CHANGED, result.join().access().orElseThrow().reason());
    }

    /** 验证同步执行器下工具重入关闭实例不会丢失已经报告的模型用量。 */
    @Test void synchronousReentrantCloseRetainsModelUsage() {
        Executor direct = Runnable::run;
        var localInteractions = new LLMInteractionService(models, LLMInteractionSettings::defaults,
                direct, runtime.scheduler, prompt -> new LLMTokenEstimate(5, "fixed-v1"));
        var handle = agent(); select((call, context) -> { handle.close(); return CompletableFuture.completedStage("已执行"); });
        doReturn(new TaskFuture<>(logger, "同步响应模型", CompletableFuture.completedFuture(response(call("同步关闭")))))
                .when(models).call(any(ILLMPrompt.class), any(ExecutionPolicy.class));
        try (var local = create(localInteractions, direct)) {
            var result = local.run(handle, request(AgentActivity.SERVICE)).toCompletableFuture().join();
            assertEquals(CANCELLED, result.status());
            assertEquals(2, result.interaction().orElseThrow().attempts().getFirst().usage().orElseThrow().getTotalTokens());
        }
    }

    /**
     * 验证真实并发登记与服务关闭不漏掉轮次，且不关闭借用线程池。
     * @throws Exception 当并发任务未在限定时间内完成时
     */
    @Test void concurrentRegistrationAndCloseCompleteEveryRequest() throws Exception {
        var port = mock(ILLMInteractionService.class);
        when(port.interact(any(), any(), any(), any(), any())).thenAnswer(invocation -> CompletableFuture.completedStage(
                new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(),
                        response().getFirstMessage().orElseThrow(), List.of(), List.of(), Map.of())));
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var timer = Executors.newSingleThreadScheduledExecutor()) {
            var local = new AgentService(gate, port, tools, new ObjectMapper(), workers, timer,
                    java.time.Clock.systemUTC(), logger);
            var handle = agent(); var start = new CountDownLatch(1);
            var outcomes = new ArrayList<CompletableFuture<AgentRunResult>>();
            for (int i = 0; i < 60; i++) {
                var request = request(AgentActivity.SERVICE);
                outcomes.add(CompletableFuture.supplyAsync(() -> {
                    try { start.await(); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new CompletionException(interrupted);
                    }
                    return local.run(handle, request);
                }, workers).thenCompose(stage -> stage));
            }
            var closer = workers.submit(() -> { start.countDown(); local.close(); });
            closer.get(5, TimeUnit.SECONDS);
            for (var outcome : outcomes) {
                var status = outcome.get(5, TimeUnit.SECONDS).status();
                assertTrue(status == SUCCESS || status == CANCELLED);
            }
            local.close(); assertFalse(handle.isClosed());
            assertFalse(workers.isShutdown()); assertFalse(timer.isShutdown());
        }
    }

    /**
     * 验证受控端口的同步或异步拒绝均停止余下工具，不伪装成可恢复失败。
     * @param asynchronous 是否通过失败阶段返回拒绝
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void hostRejectionStopsBatchForSyncAndAsyncTools(boolean asynchronous) {
        var attempts = new AtomicInteger(); select((call, context) -> {
            attempts.incrementAndGet(); var rejection = new CompletionException(new LLMOperationRejectedException());
            if (asynchronous) { return CompletableFuture.failedStage(rejection); }
            throw rejection;
        });
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        reply(response(call("a"), call("b")));
        assertEquals(CANCELLED, result.join().status()); assertEquals(1, attempts.get()); assertEquals(1, replies.size());
    }

    /** 验证普通业务工具失败仍可恢复，但真实失败状态不会包装为成功动作。 */
    @Test void recoverableToolFailureKeepsActualStatus() {
        select((call, context) -> CompletableFuture.failedStage(new IllegalStateException("工具内部诊断")));
        var result = service.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
        reply(response(call("a"))); assertEquals(2, replies.size()); reply(response());
        assertEquals(SUCCESS, result.join().status());
        var tool = result.join().interaction().orElseThrow().tools().getFirst();
        assertEquals(ToolInvocationResult.Status.FAILED, tool.status()); assertFalse(tool.output().contains("工具内部诊断"));
    }

    /** 验证 Spring 执行装配不读取配置、创建实例或使用执行器，默认无工具。 */
    @Test void assemblyCreatesNoRuntimeWork() {
        var config = new AgentExecutionConfig(); var executor = mock(ExecutorService.class);
        var beans = new DefaultListableBeanFactory();
        var built = config.agentTurnService(gate, interactions, beans.getBeanProvider(IAgentToolSource.class), new ObjectMapper(),
                executor, runtime.scheduler, logger, mock(IAgentMemoryReferences.class));
        try (built) { verifyNoInteractions(scopes, readiness, models, executor); }
    }

    /** 验证显式工具源直接由 Bean 提供者选择，不与默认空实现竞争或依赖配置扫描顺序。 */
    @Test void explicitToolSourceIsSelectedAtServiceAssembly() {
        var config = new AgentExecutionConfig(); var executor = mock(ExecutorService.class);
        doAnswer(invocation -> { runtime.execute(invocation.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("明确工具源", tools);
        select((call, context) -> CompletableFuture.completedStage("工具结果"));
        var references = mock(IAgentMemoryReferences.class);
        var fixed = new org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference(request(AgentActivity.SERVICE).actor().scope(),
                org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId.SHARED, 0, 0);
        when(references.resolve(any())).thenReturn(CompletableFuture.completedStage(fixed));
        when(references.verify(any())).thenReturn(CompletableFuture.completedStage(null));
        var built = config.agentTurnService(gate, interactions, beans.getBeanProvider(IAgentToolSource.class), new ObjectMapper(),
                executor, runtime.scheduler, logger, references);
        try (built) {
            var result = built.run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll();
            assertEquals("work", requireOptions(prompts.getFirst()).getTools().getFirst().getName());
            reply(response()); assertEquals(SUCCESS, result.join().status());
            verify(tools).select(any(), any(), any());
        }
        verify(executor, never()).shutdown();
    }

    /**
     * 验证可选 Prompt 端口通过真实 Spring Bean 选择，构造无 I/O 且共享资源所有权保持稳定。
     * <br>2026/11/06：两个未装配的组件资料服务不得参与全局选择或执行。
     */
    @Test void springAssemblySelectsExplicitPromptSourceWithoutEagerReads() {
        var executor = mock(ExecutorService.class);
        doAnswer(invocation -> { runtime.execute(invocation.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        var source = mock(org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.class);
        var componentSource = mock(IAgentComponentPromptSource.class);
        var otherComponentSource = mock(IAgentComponentPromptSource.class);
        when(source.resolve(any(), any(), any(), any())).thenReturn(CompletableFuture.completedStage(List.of()));
        var references = mock(IAgentMemoryReferences.class);
        var fixed = new org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference(request(AgentActivity.SERVICE).actor().scope(),
                org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId.SHARED, 0, 0);
        when(references.resolve(any())).thenReturn(CompletableFuture.completedStage(fixed));
        when(references.verify(any())).thenReturn(CompletableFuture.completedStage(null));
        try (var context = new AnnotationConfigApplicationContext()) {
            var beans = context.getBeanFactory(); beans.registerSingleton("agentAccessGate", gate);
            beans.registerSingleton("llmInteractionService", interactions); beans.registerSingleton("objectMapper", new ObjectMapper());
            beans.registerSingleton("virtualExecutor", executor); beans.registerSingleton("taskExecutor", runtime.scheduler);
            beans.registerSingleton("projectLogger", logger); beans.registerSingleton("memoryReferences", references); beans.registerSingleton("promptSource", source);
            beans.registerSingleton("componentSource", componentSource);
            beans.registerSingleton("otherComponentSource", otherComponentSource);
            context.register(AgentExecutionConfig.class); context.refresh();
            verifyNoInteractions(source, executor, scopes, readiness);
            var assembled = context.getBean(IAgentTurnService.class); assertSame(assembled, context.getBean("agentTurnService"));
            var result = context.getBean(AgentService.class).run(agent(), request(AgentActivity.SERVICE)).toCompletableFuture(); runtime.runAll(); reply(response());
            assertEquals(SUCCESS, result.join().status()); verify(source).resolve(any(), any(), any(), any());
            verifyNoInteractions(componentSource, otherComponentSource);
        }
        verify(executor, never()).shutdown(); verify(runtime.scheduler, never()).shutdown();
    }

    /** 验证最小 Spring 装配支持接口注入和实际关闭方法，销毁时结束冷轮次并保留借用执行器。 */
    @Test void springBeanDestructionCompletesOwnedColdExecution() {
        var executor = mock(ExecutorService.class);
        var handle = agent();
        final AgentRunExecution cold;
        try (var context = new AnnotationConfigApplicationContext()) {
            var beans = context.getBeanFactory();
            beans.registerSingleton("agentAccessGate", gate);
            beans.registerSingleton("llmInteractionService", interactions);
            beans.registerSingleton("objectMapper", new ObjectMapper());
            beans.registerSingleton("virtualExecutor", executor);
            beans.registerSingleton("taskExecutor", runtime.scheduler);
            beans.registerSingleton("projectLogger", logger);
            beans.registerSingleton("memoryReferences", mock(IAgentMemoryReferences.class));
            context.register(AgentExecutionConfig.class);
            context.refresh();
            var assembled = context.getBean(IAgentTurnService.class);
            assertSame(assembled, context.getBean(AgentService.class));
            cold = assembled.prepare(handle, request(AgentActivity.SERVICE));
            assertFalse(cold.result().toCompletableFuture().isDone());
        }
        assertEquals(CANCELLED, cold.result().toCompletableFuture().join().status());
        assertFalse(handle.isClosed());
        verifyNoInteractions(scopes, readiness, models, executor);
        verify(runtime.scheduler, never()).shutdown();
    }
}
