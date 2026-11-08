package org.zexnocs.teanekoagent.runtime.prompt;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageHistory;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentFactory;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentRegistry;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.AgentService;
import org.zexnocs.teanekoagent.runtime.AgentUserText;
import org.zexnocs.teanekoagent.runtime.IAgentService;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationEngine;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationKey;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationOptions;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationService;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationSettings;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationWindow;
import org.zexnocs.teanekoagent.runtime.conversation.AgentQueueSettings;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.agent.automation.skill.ISkillCatalog;
import org.zexnocs.teanekoplugin.agent.automation.skill.SkillCatalogSnapshot;
import org.zexnocs.teanekoplugin.agent.automation.skill.SkillDefinition;
import org.zexnocs.teanekoplugin.agent.automation.skill.SkillPolicy;
import org.zexnocs.teanekoplugin.agent.automation.skill.adapter.AgentSkills;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 通过真实 Agent 邮箱和 LLM 管线验证异步资料、历史隔离、资源停止和重放边界。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
 * <br>2026/11/05：拒绝重放时保留安全原因，不恢复已撤销来源或重复模型请求。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/05：整体禁用工具仍复核已提供资料的依赖，不发起无效模型请求。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.3-l
 */
class AgentPromptIntegrationTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("test",
            new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    private final ILogger logger = mock(ILogger.class);
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final LLMMessageCodec codec = new LLMMessageCodec(mapper);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final AtomicReference<AgentConversationOptions> options = new AtomicReference<>(AgentConversationOptions.defaults());
    private final SwitchableSource source = new SwitchableSource();
    private final AtomicReference<AgentRunRequest> toolRequest = new AtomicReference<>();
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final AgentAccessGate gate = new AgentAccessGate(registry,
            ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE),
            ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(new AgentRuntimeSnapshot(
                    new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(20).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults()))), logger);
    private final AgentService turns = new AgentService(gate, new LLMInteractionService(models,
            LLMInteractionSettings::defaults, runtime, runtime.scheduler), (definition, request, config) -> {
                toolRequest.set(request);
                return List.of(new LLMToolBinding(LLMTool.builder().name("work").build(), (call, context) -> CompletableFuture.completedStage("工具完成")));
            }, mapper, runtime, runtime.scheduler, runtime.clock, logger, null, source);
    private final AgentConversationService mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults,
            runtime, runtime.scheduler, runtime.clock, logger, AgentConversationSettings::defaults, (agent, request) -> options.get(), mapper);

    /** 注册受控模型响应，资料及轮次均使用真实异步管线。 */
    AgentPromptIntegrationTest() {
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0)); var pending = new CompletableFuture<ILLMResult>(); replies.add(pending);
            return new TaskFuture<>(logger, "临时资料离线模型", pending);
        });
    }

    /** 关闭自己拥有的服务，确认资料等待和 LLM 阶段没有遗留计时器。 */
    @AfterEach void cleanup() {
        mailbox.close(); turns.close(); registry.close(); runtime.runAll(); assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 构造具有当前原文凭据的真实用户请求，历史不能生成该凭据。
     * @return 同一群的独立请求
     */
    private AgentRunRequest request() {
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "用户", UUID.randomUUID().toString(), "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("原始问题").build()).build()),
                null, null, new AgentUserText(actor.sourceId(), "原始问题"));
    }

    /**
     * 构造带原始工具 ID 和透明供应商状态的统一响应。
     * @param tools 是否请求真实工具往返
     * @return 明确 usage 的模型结果
     */
    private ILLMResult response(boolean tools) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder()
                .index(0).finishReason(tools ? "tool_calls" : "stop").message(LLMAssistantMessage.builder()
                        .contents(new LLMContentListBuilder().addText("正常回答").build()).providerMetadata(Map.of("reasoning_content", "透明状态"))
                        .toolCalls(tools ? List.of(LLMToolCall.builder().id("固定调用").name("work").arguments("{}").build()) : List.of()).build()).build())).build();
    }

    /**
     * 读取真实邮箱已提交窗口。
     * @return 当前成功历史窗口
     */
    private AgentConversationWindow window() {
        return mailbox.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT);
    }

    /**
     * 度量消息内容，断言时只读取真实协议内容。
     * @param messages 实际消息列表
     * @return 完整序列化文本
     */
    private String text(List<ILLMMessage> messages) { return mapper.writeValueAsString(messages); }

    /**
     * 复用本类的真实 Agent/LLM 执行设施，只装饰显式工具及原资料源。
     * @param skills 本轮 Skill 能力
     * @return 由测试拥有的独立轮次服务
     */
    private AgentService skillService(AgentSkills skills) {
        return new AgentService(gate, new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler),
                skills.tools((definition, request, config) -> List.of(new LLMToolBinding(LLMTool.builder().name("work").build(),
                        (call, context) -> CompletableFuture.completedStage("工具完成")))), mapper, runtime, runtime.scheduler, runtime.clock, logger, null, skills.prompts(source));
    }
    /**
     * 用统一消息协议构造 Skill 加载或原工具调用，不替换真实执行链。
     * @param name 实际工具名称
     * @param arguments 原 JSON 参数
     * @return 明确请求工具往返的响应
     */
    private ILLMResult skillCall(String name, String arguments) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0).finishReason("tool_calls")
                .message(LLMAssistantMessage.builder().contents(List.of()).toolCalls(List.of(LLMToolCall.builder().id("调用-" + name).name(name).arguments(arguments).build())).build()).build())).build();
    }
    /**
     * 验证按需读取、实际工具依赖和总预算，正文只供原轮次使用，关闭后拒绝旧完成重放。
     * 2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
     * 2026/11/05：末次模型保留可信定义及 none 约束，不再以删除定义代表禁止调用。
     */
    @Test void skillWorkflowUsesOriginalBudgetAndKeepsInstructionsOutOfHistory() {
        var catalog = mock(ISkillCatalog.class);
        when(catalog.snapshot()).thenReturn(new SkillCatalogSnapshot(List.of(new SkillDefinition("sample", "流程说明", 1, true, List.of("work"), "专用于原轮次的流程正文"))));
        var policy = new AtomicReference<>(new SkillPolicy(true, List.of("sample")));
        var skills = new AgentSkills(catalog, ignored -> policy.get(), mapper, runtime);
        try (var service = skillService(skills); var conversations = new AgentConversationService(service, AgentQueueSettings::defaults,
                runtime, runtime.scheduler, runtime.clock, logger, AgentConversationSettings::defaults, (agent, request) -> options.get(), mapper)) {
            var input = request(); var result = conversations.run(handle, input).toCompletableFuture(); runtime.runAll();
            assertTrue(text(prompts.getFirst().getMessages()).contains("流程说明")); assertFalse(text(prompts.getFirst().getMessages()).contains("专用于原轮次的流程正文"));
            replies.getLast().complete(skillCall("skill_load", "{\"id\":\"sample\"}")); runtime.runAll();
            assertTrue(text(prompts.getLast().getMessages()).contains("专用于原轮次的流程正文"));
            replies.getLast().complete(skillCall("work", "{}")); runtime.runAll();
            assertTrue(text(prompts.getLast().getMessages()).contains("工具完成"));
            var finalOptions = Objects.requireNonNull(prompts.getLast().getOptions());
            assertEquals("none", finalOptions.getToolChoice());
            assertTrue(finalOptions.getTools().stream().anyMatch(tool -> tool.getName().equals("work")));
            replies.getLast().complete(response(false)); runtime.runAll(); var done = result.join();
            assertEquals(AgentRunResult.Status.SUCCESS, done.status()); assertEquals(3, done.interaction().orElseThrow().attempts().size());
            var window = conversations.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT);
            LLMMessageHistory.validateComplete(window.messages()); assertFalse(text(window.messages()).contains("专用于原轮次的流程正文"));
            assertTrue(text(window.messages()).contains("后续需要时应重新调用工具"));
            policy.set(new SkillPolicy(false, List.of())); var replay = conversations.run(handle, input).toCompletableFuture(); runtime.runAll();
            assertNotEquals(AgentRunResult.Status.SUCCESS, replay.join().status()); assertEquals(3, prompts.size());
        }
    }
    /** 验证依赖缺失不调用模型，目录换代阻止迟到结果，省略目录不注册隐藏工具。 */
    @Test void skillDependenciesReloadAndOmissionRejectStaleCapabilities() {
        var catalog = mock(ISkillCatalog.class); var snapshot = new AtomicReference<>(new SkillCatalogSnapshot(List.of(
                new SkillDefinition("sample", "流程说明", 1, true, List.of("forbidden"), "原始正文"))));
        when(catalog.snapshot()).thenAnswer(ignored -> snapshot.get());
        var skills = new AgentSkills(catalog, ignored -> new SkillPolicy(true, List.of("sample")), mapper, runtime);
        try (var service = skillService(skills)) {
            var denied = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
            assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, denied.join().status()); assertTrue(prompts.isEmpty());
            snapshot.set(new SkillCatalogSnapshot(List.of(new SkillDefinition("sample", "流程说明", 1, true, List.of(), "原始正文"))));
            var pending = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
            snapshot.set(new SkillCatalogSnapshot(List.of(new SkillDefinition("sample", "流程说明", 1, true, List.of(), "换代正文"))));
            replies.getLast().complete(response(false)); runtime.runAll();
            assertEquals(AgentRunResult.Status.STALE_CONVERSATION, pending.join().status()); assertTrue(pending.join().answer().isEmpty());
            var base = skills.tools((definition, input, config) -> List.of());
            assertTrue(base.select(handle.definition(), request(), gateRuntime(), new AgentPromptInput(request().messages(), List.of(), 1)).isEmpty());
        }
    }
    /** 2026/11/05：白名单删除 Skill 依赖或加载器时，在实际模型调用前拒绝整轮。 */
    @Test
    void finalToolAllowlistCannotLeaveUnusableSkillCatalog() {
        var catalog = mock(ISkillCatalog.class);
        when(catalog.snapshot()).thenReturn(new SkillCatalogSnapshot(List.of(
                new SkillDefinition("sample", "流程说明", 1, true, List.of("work"), "原始正文"))));
        var skills = new AgentSkills(catalog, ignored -> new SkillPolicy(true, List.of("sample")), mapper, runtime);
        for (var allowed : List.of("skill_load", "work")) {
            var assembly = org.zexnocs.teanekoagent.component.AgentAssembly.parse(
                    "{\"version\":1,\"components\":{\"llm\":{},\"tool\":{\"allowedTools\":\"" + allowed + "\"},\"skill\":{}}}");
            var boundedGate = new AgentAccessGate(registry,
                    ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE, "", assembly),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(gateRuntime())), logger);
            try (var service = new AgentService(boundedGate,
                    new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler),
                    skills.tools((definition, request, config) -> List.of(new LLMToolBinding(
                            LLMTool.builder().name("work").build(), (call, context) -> CompletableFuture.completedStage("完成")))),
                    mapper, runtime, runtime.scheduler, runtime.clock, logger, null, skills.prompts(source))) {
                var result = service.run(handle, request()).toCompletableFuture();
                runtime.runAll();
                assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status());
                assertTrue(prompts.isEmpty());
            }
        }
    }

    /**
     * 从本夹具已有准入取得运行快照，不重复构造另一套模型参数。
     * @return 实际就绪设置
     */
    private AgentRuntimeSnapshot gateRuntime() { return gate.evaluate(handle, request().actor(), AgentActivity.SERVICE).runtime().orElseThrow(); }

    /** 2026/11/05：没有工具活动时，空工具集也须经过资料依赖检查，但不调用工具选择器。 */
    @Test
    void disabledToolsStillVerifyExistingPromptDependencies() {
        var disabled = new AgentFactory(registry).create(new AgentDefinition("无工具",
                new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.SERVICE)));
        var toolSource = mock(IAgentToolSource.class);
        doThrow(new org.zexnocs.teanekoplugin.agent.expression.sticker.StickerReplyException())
                .when(toolSource).verifySelection(any(), any(), eq(List.of()));
        try (var service = new AgentService(gate,
                new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler),
                toolSource, mapper, runtime, runtime.scheduler, runtime.clock, logger, null, source)) {
            var result = service.run(disabled, request()).toCompletableFuture();
            runtime.runAll();
            assertTrue(result.isDone());
            assertTrue(result.join().answer().isEmpty());
            assertTrue(prompts.isEmpty());
            verify(toolSource).verifySelection(any(), any(), eq(List.of()));
            verifyNoMoreInteractions(toolSource);
        }
    }

    /** 验证连续轮次只携带本轮资料，原文授权与工具选择不接收资料伪造，实际诊断仍保留资料。 */
    @Test void temporaryDataReachesModelWithoutAccumulatingInHistoryOrAuthorization() {
        var first = request(); var result = mailbox.run(handle, first).toCompletableFuture(); runtime.runAll();
        assertTrue(text(prompts.getFirst().getMessages()).contains("临时资料甲"));
        assertFalse(text(toolRequest.get().messages()).contains("临时资料甲")); assertSame(first.userText(), toolRequest.get().userText());
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        assertNotNull(result.join().promptTranscript()); assertTrue(text(result.join().interaction().orElseThrow().transcript()).contains("临时资料甲"));
        assertFalse(text(result.join().committableTranscript().orElseThrow()).contains("临时资料甲")); assertEquals(2, window().messages().size());
        source.resolver = () -> CompletableFuture.completedStage(List.of(new AgentPromptFragment("memory", "临时资料乙")));
        var next = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertTrue(text(prompts.getLast().getMessages()).contains("临时资料乙")); assertFalse(text(prompts.getLast().getMessages()).contains("临时资料甲"));
        replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, next.join().status());
        assertEquals(4, window().messages().size()); assertFalse(text(window().messages()).contains("临时资料"));
    }

    /** 验证真实工具往返后仍保留调用 ID、供应商状态和原消息，临时资料不进入完整工具历史。 */
    @Test void actualToolRoundKeepsProtocolAndProjectionSeparate() {
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll(); assertEquals(2, prompts.size());
        assertTrue(source.verifications.get() >= 4); assertTrue(text(prompts.getLast().getMessages()).contains("临时资料甲"));
        replies.getLast().complete(response(false)); runtime.runAll(); var done = result.join();
        assertEquals(AgentRunResult.Status.SUCCESS, done.status()); LLMMessageHistory.validateComplete(window().messages());
        assertEquals(4, window().messages().size()); assertTrue(text(window().messages()).contains("固定调用"));
        assertTrue(text(window().messages()).contains("透明状态")); assertFalse(text(window().messages()).contains("临时资料甲"));
        assertEquals(2, done.interaction().orElseThrow().attempts().size());
    }

    /** 验证等待来源期间不启动模型；完成线程只冻结列表，随后业务域使用冻结结果。 */
    @Test void pendingSourceIsAwaitedAndFrozenBeforeBusinessDispatch() {
        var pending = new CompletableFuture<List<AgentPromptFragment>>(); source.resolver = () -> pending;
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll(); assertTrue(prompts.isEmpty());
        var data = new ArrayList<>(List.of(new AgentPromptFragment("memory", "完成时资料"))); pending.complete(data); data.clear();
        assertTrue(prompts.isEmpty()); runtime.runAll(); assertTrue(text(prompts.getFirst().getMessages()).contains("完成时资料"));
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
    }

    /** 验证取消只停止借用观察，迟到资料不发起模型或写历史，不取消来源自己的事务阶段。 */
    @Test void cancellationDuringSourceWaitIgnoresLateResult() {
        var pending = new CompletableFuture<List<AgentPromptFragment>>(); source.resolver = () -> pending; var request = request();
        var result = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        assertEquals(IAgentService.CancelResult.REQUESTED, mailbox.cancel(handle, scope, request.requestId(), request.actor().userId().orElseThrow()));
        runtime.runAll(); assertEquals(AgentRunResult.Status.CANCELLED, result.join().status()); assertFalse(pending.isCancelled());
        pending.complete(List.of(new AgentPromptFragment("memory", "取消后到达"))); runtime.runAll(); assertTrue(prompts.isEmpty()); assertTrue(window().messages().isEmpty());
    }

    /** 验证资料等待属于整轮截止时间，超时不用真实休眠，也不提前消耗模型次数。 */
    @Test void sourceWaitUsesWholeTurnDeadline() {
        var pending = new CompletableFuture<List<AgentPromptFragment>>(); source.resolver = () -> pending;
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll(); runtime.advance(Duration.ofSeconds(121)); runtime.runAll();
        assertEquals(AgentRunResult.Status.TIMED_OUT, result.join().status()); assertTrue(prompts.isEmpty()); assertFalse(pending.isCancelled());
        pending.complete(List.of()); runtime.runAll(); assertTrue(window().messages().isEmpty());
    }

    /** 验证无效阶段/值、重复来源及同步/异步异常均安全终止，不泄漏来源错误正文或调用模型。 */
    @Test void malformedAndFailedSourcesFinishWithoutSensitiveErrors() {
        List<Supplier<CompletionStage<List<AgentPromptFragment>>>> failures = List.of(
                () -> null, () -> CompletableFuture.completedStage(null),
                () -> CompletableFuture.completedStage(List.of(new AgentPromptFragment("same", "甲"), new AgentPromptFragment("same", "乙"))),
                () -> { throw new IllegalStateException("私密来源正文"); },
                () -> CompletableFuture.failedStage(new IllegalStateException("私密来源正文")));
        for (var failure : failures) {
            source.resolver = failure; var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
            assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status()); assertFalse(mapper.writeValueAsString(result.join()).contains("私密来源正文"));
        }
        assertTrue(prompts.isEmpty()); assertTrue(window().messages().isEmpty());
    }

    /** 验证模型前的动态来源换代和关闭分别映射为过期与跳过，异步校验失败不继续模型。 */
    @Test void changingOrDisabledSourceStopsBeforeModel() {
        for (var reason : List.of(AgentPromptException.Reason.CHANGED, AgentPromptException.Reason.DISABLED)) {
            source.verifications.set(0);
            source.verifier = input -> source.verifications.get() == 1 ? CompletableFuture.completedStage(null)
                    : CompletableFuture.failedStage(new AgentPromptException(reason));
            var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
            assertEquals(reason == AgentPromptException.Reason.CHANGED ? AgentRunResult.Status.STALE_CONVERSATION : AgentRunResult.Status.SKIPPED, result.join().status());
        }
        assertTrue(prompts.isEmpty());
    }

    /** 验证回答交付前资料失效会隐藏回答，保留实际交互/usage 且不提交正常历史。 */
    @Test void finalSourceInvalidationHidesAnswerButRetainsActualDiagnostic() {
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
        source.verifier = input -> CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED));
        replies.getFirst().complete(response(false)); runtime.runAll(); var done = result.join();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, done.status()); assertTrue(done.answer().isEmpty());
        assertEquals(1, done.interaction().orElseThrow().attempts().size()); assertTrue(text(done.interaction().orElseThrow().transcript()).contains("临时资料甲"));
        assertTrue(window().messages().isEmpty());
    }

    /**
     * 验证资料结果的进行中及完成后重放明确不可用，不重新读取资料、调用模型或重复工具。
     * 2026/11/05：检查拒绝原因传递到发送侧，不用通用失效错误覆盖来源检查结论。
     */
    @Test void supplementaryDataResultsCannotReplayWithoutSourceAuthorizationProtocol() {
        var request = request(); var original = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        var inflight = mailbox.retry(handle, request).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, original.join().status());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, inflight.join().status()); assertTrue(inflight.join().answer().isEmpty());
        assertTrue(inflight.join().failureDescription().contains("原资料源不允许重放"));
        var replay = mailbox.retry(handle, request).toCompletableFuture(); var duplicate = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, replay.join().status()); assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, duplicate.join().status());
        assertEquals(1, source.resolutions.get()); assertEquals(1, prompts.size()); assertEquals(1, window().turns().size());
    }

    /** 2026/11/05：资料重放抛错时保留首条固定原因，旧答案与模型原文均不能继续交付。 */
    @Test
    void replayFailureRetainsSafeSourceReason() {
        var request = request();
        var original = mailbox.run(handle, request).toCompletableFuture();
        runtime.runAll();
        replies.getFirst().complete(response(false));
        runtime.runAll();
        assertEquals(AgentRunResult.Status.SUCCESS, original.join().status());
        source.replayVerifier = () -> CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED));
        var replay = mailbox.retry(handle, request).toCompletableFuture();
        runtime.runAll();
        assertTrue(replay.join().answer().isEmpty());
        assertEquals("当前参考资料的绑定或代次已变化。", replay.join().failureDescription());
        assertEquals(1, prompts.size());
    }

    /** 验证原输入恰好填满预算时整项省略资料，实际选项覆盖调用者提示且普通缓存可重放。 */
    @Test void exactInputBudgetOmitsAllDataAndPreservesNormalReplay() {
        var request = request(); var stamp = new AgentConversationStamp(new AgentConversationKey(handle.id(), scope), UUID.randomUUID());
        var prepared = new AgentConversationEngine(mapper).prepare(request, stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var bytes = codec.signature(prepared.request().messages()).bytes(); options.set(new AgentConversationOptions(40, bytes, 32, 1048576));
        var hinted = new AgentRunRequest(request.requestId(), request.actor(), request.activity(), request.messages(), null, null, request.userText(), 1);
        var result = mailbox.run(handle, hinted).toCompletableFuture(); runtime.runAll();
        assertEquals(bytes, codec.signature(prompts.getFirst().getMessages()).bytes()); assertEquals(0, source.verifications.get());
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        assertEquals(1, Objects.requireNonNull(result.join().promptTranscript()).input().omittedFragments());
        var replay = mailbox.retry(handle, hinted).toCompletableFuture(); runtime.runAll(); assertSame(result.join(), replay.join()); assertEquals(1, prompts.size());
    }

    /** 验证独立轮次同样执行宿主输入上限，原问题超限不通过删资料或增加模型预算绕过。 */
    @Test void independentTurnRejectsOversizedOriginalBeforeModel() {
        var request = request(); var tooSmall = new AgentRunRequest(request.requestId(), request.actor(), request.activity(), request.messages(), null, null, request.userText(), 1);
        var result = turns.run(handle, tooSmall).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.INPUT_TOO_LARGE, result.join().status()); assertTrue(prompts.isEmpty());
    }

    /** 验证资料重放等待有独立期限和取消，迟到允许不公开回答，不停止原请求或取消来源阶段。 */
    @Test void promptReplayObservationHasIndependentCancellationAndDeadline() {
        var request = request(); var original = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, original.join().status());
        var cancelledCheck = new CompletableFuture<Boolean>(); source.replayVerifier = () -> cancelledCheck;
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var cancelled = mailbox.retry(handle, request, parent).toCompletableFuture(); runtime.runAll(); assertFalse(cancelled.isDone());
            parent.cancel(); runtime.runAll(); assertEquals(AgentRunResult.Status.CANCELLED, cancelled.join().status());
            assertFalse(cancelledCheck.isCancelled()); cancelledCheck.complete(true); runtime.runAll(); assertTrue(cancelled.join().answer().isEmpty());
        }
        var timedCheck = new CompletableFuture<Boolean>(); source.replayVerifier = () -> timedCheck;
        var timed = mailbox.retry(handle, request).toCompletableFuture(); runtime.runAll(); runtime.advance(Duration.ofSeconds(31)); runtime.runAll();
        assertEquals(AgentRunResult.Status.TIMED_OUT, timed.join().status()); timedCheck.complete(true); runtime.runAll(); assertTrue(timed.join().answer().isEmpty());
        source.replayVerifier = () -> CompletableFuture.completedStage(true);
        var allowed = mailbox.retry(handle, request).toCompletableFuture(); runtime.runAll(); assertSame(original.join(), allowed.join());
        assertEquals(1, prompts.size()); assertEquals(1, source.resolutions.get());
    }

    /**
     * 可控制资料等待和动态校验的端口替身，不替换真实执行/提交/资源拥有者。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    private static final class SwitchableSource implements IAgentPromptSource {
        Supplier<CompletionStage<List<AgentPromptFragment>>> resolver = () -> CompletableFuture.completedStage(List.of(new AgentPromptFragment("memory", "临时资料甲")));
        Function<AgentPromptInput, CompletionStage<Void>> verifier = input -> CompletableFuture.completedStage(null);
        Supplier<CompletionStage<Boolean>> replayVerifier = () -> CompletableFuture.completedStage(false);
        final AtomicInteger resolutions = new AtomicInteger();
        final AtomicInteger verifications = new AtomicInteger();

        /**
         * 返回当前受控阶段，构造时不读取来源。
         * @param definition 实例定义
         * @param request 固定原始请求
         * @param runtime 运行快照
         * @param control 借用轮次控制
         * @return 由测试推进的来源阶段
         */
        @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request,
                AgentRuntimeSnapshot runtime, ExecutionControl control) { resolutions.incrementAndGet(); return resolver.get(); }

        /**
         * 动态检查实际保留资料，不改变旧输入。
         * @param request 固定请求
         * @param input 已冻结的实际布局
         * @param control 借用控制
         * @return 当前校验阶段
         */
        @Override public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
            verifications.incrementAndGet(); return verifier.apply(input);
        }

        /**
         * 显式控制当前资料重放资格，未指定时继续使用默认拒绝。
         * @param request 原可信请求
         * @param input 原资料布局
         * @param control 观察借用控制
         * @return 由测试推进的只读校验
         */
        @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
            return replayVerifier.get();
        }
    }
}
