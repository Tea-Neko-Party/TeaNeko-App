package org.zexnocs.teanekoagent.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationKey;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.*;
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
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 经真实独立 Agent/LLM 管线验证引用固定、异步守卫、预算、取消与迟到结果隔离。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class AgentMemoryRuntimeTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("空间引用测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final MemoryNamespaceReference fixed = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final AtomicReference<MemoryNamespaceReference> current = new AtomicReference<>(fixed);
    private final AtomicReference<AgentScopePolicy> policy = new AtomicReference<>(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE));
    private final IAgentMemoryReferences references = mock(IAgentMemoryReferences.class);
    private final AtomicReference<Function<AgentScope, CompletionStage<MemoryNamespaceReference>>> resolve = new AtomicReference<>(ignored -> CompletableFuture.completedStage(current.get()));
    private final AtomicReference<Function<MemoryNamespaceReference, CompletionStage<Void>>> verify = new AtomicReference<>(expected ->
            expected.equals(current.get()) ? CompletableFuture.completedStage(null)
                    : CompletableFuture.failedStage(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED)));
    private final AtomicInteger checks = new AtomicInteger();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicReference<EntitySnapshot> toolContext = new AtomicReference<>();
    private final ILogger logger = mock(ILogger.class);
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final AgentRuntimeSnapshot ready = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder()
            .provider("test").model("离线模型").maxTokens(20).build()), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
    private final AgentAccessGate gate = new AgentAccessGate(registry, ignored -> policy.get(),
            ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(ready)), logger);
    private final AgentService service = new AgentService(gate, new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler),
            (definition, request, config) -> List.of(new LLMToolBinding(LLMTool.builder().name("work").build(), (call, context) -> {
                toolCalls.incrementAndGet(); toolContext.set(context.bindings()); return CompletableFuture.completedStage("工具结果");
            })), new ObjectMapper(), runtime, runtime.scheduler, runtime.clock, logger, references);

    /** 接入可延迟引用及模型端口，保留真实控制、工具往返和结束行为。 */
    AgentMemoryRuntimeTest() {
        when(references.resolve(any())).thenAnswer(call -> resolve.get().apply(call.getArgument(0)));
        when(references.verify(any())).thenAnswer(call -> { checks.incrementAndGet(); return verify.get().apply(call.getArgument(0)); });
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
            prompts.add(call.getArgument(0)); var pending = new CompletableFuture<ILLMResult>(); replies.add(pending);
            return new TaskFuture<>(logger, "空间引用离线模型", pending);
        });
    }

    /** 关闭测试拥有的轮次和实例，借用执行器不被服务关闭且没有遗留计时器。 */
    @AfterEach void cleanup() { service.close(); registry.close(); runtime.runAll(); assertEquals(0, runtime.pendingTimers()); }

    /**
     * 创建当前群的独立请求，不从消息正文读取 namespace 或 actor。
     * @return 固定可信来源的新输入
     */
    private AgentRunRequest request() {
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "平台用户", "消息", "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE, List.of(LLMUserMessage.builder()
                .contents(new LLMContentListBuilder().addText("输入").build()).build()));
    }

    /**
     * 构造含 usage 的普通或工具请求结果。
     * @param tool 是否发起工具请求
     * @return 完整统一模型结果
     */
    private static LLMResult response(boolean tool) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0)
                .finishReason(tool ? "tool_calls" : "stop").message(LLMAssistantMessage.builder()
                        .contents(new LLMContentListBuilder().addText("回答").build())
                        .toolCalls(tool ? List.of(LLMToolCall.builder().id("调用").name("work").arguments("{}").build()) : List.of()).build()).build())).build();
    }

    /** 验证构造及冷准备不解析引用，启动后等待实际解析完成。 */
    @Test void coldExecutionDoesNotResolveAndStartedExecutionWaits() {
        var pending = new CompletableFuture<MemoryNamespaceReference>(); resolve.set(ignored -> pending);
        var cold = service.prepare(handle, request()); verifyNoInteractions(references, models);
        var result = cold.start().toCompletableFuture(); runtime.runAll(); assertFalse(result.isDone()); verifyNoInteractions(models);
        pending.complete(fixed); runtime.runAll(); assertEquals(1, replies.size());
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
    }

    /** 验证工具只能从可信组件取得固定引用，结果保留来源且不写入模型透明状态。 */
    @Test void successfulToolTurnUsesFixedReferenceWithoutPromptAuthority() {
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll(); assertEquals(1, toolCalls.get());
        var seen = toolContext.get(); assertNotNull(seen); assertEquals(fixed, seen.require(AgentComponents.MEMORY_REFERENCE));
        replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        assertEquals(fixed, result.join().memoryReference()); assertEquals(2, result.join().interaction().orElseThrow().attempts().size());
        for (var prompt : prompts) { assertTrue(prompt.getMessages().stream().allMatch(message -> message.getProviderMetadata().isEmpty())); }
    }

    /** 验证模型响应期间数据代次变化隐藏回答，保留原引用、已观察结果及 usage。 */
    @Test void generationChangeDuringModelStopsFinalDelivery() {
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1));
        replies.getFirst().complete(response(false)); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); assertTrue(result.join().answer().isEmpty());
        assertEquals(fixed, result.join().memoryReference()); assertEquals(1, result.join().interaction().orElseThrow().attempts().size());
    }

    /** 验证模型发出工具请求后切换 namespace，异步守卫阻止实际工具和后续模型请求。 */
    @Test void changedBindingPreventsToolSideEffect() {
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        current.set(new MemoryNamespaceReference(scope, new MemoryNamespaceId("other"), 1, 0));
        replies.getFirst().complete(response(true)); runtime.runAll();
        assertEquals(0, toolCalls.get()); assertEquals(1, replies.size());
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); assertTrue(result.join().answer().isEmpty());
    }

    /** 验证模型前异步守卫未完成时不预占请求预算，允许后只执行一次且不关闭父控制。 */
    @Test void delayedModelGuardDoesNotSpendOrResetParentBudget() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> checks.get() == 2 ? pending : CompletableFuture.completedStage(null));
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var result = service.run(handle, request(), parent).toCompletableFuture(); runtime.runAll();
            assertEquals(0, parent.budget().used(LLMCallLimits.MODEL_REQUESTS)); assertTrue(replies.isEmpty());
            pending.complete(null); runtime.runAll(); assertEquals(1, replies.size());
            replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
            assertEquals(1, parent.budget().used(LLMCallLimits.MODEL_REQUESTS)); service.close(); assertDoesNotThrow(parent::checkActive);
        }
    }

    /** 验证取消等待解析的轮次立即结束，迟到引用不能再发起模型。 */
    @Test void cancellingPendingResolutionDoesNotStartLateModel() {
        var pending = new CompletableFuture<MemoryNamespaceReference>(); resolve.set(ignored -> pending);
        var cold = service.prepare(handle, request()); var result = cold.start().toCompletableFuture(); runtime.runAll();
        assertTrue(cold.cancel()); runtime.runAll(); assertEquals(AgentRunResult.Status.CANCELLED, result.join().status());
        pending.complete(fixed); runtime.runAll(); assertTrue(replies.isEmpty()); assertEquals(0, toolCalls.get());
    }

    /** 验证模型守卫耗时属于模型阶段截止时间，超时后迟到允许不能继续请求。 */
    @Test void modelGuardTimeoutDoesNotLaunchLateRequest() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> checks.get() == 2 ? pending : CompletableFuture.completedStage(null));
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        runtime.advance(Duration.ofSeconds(61)); runtime.runAll(); assertEquals(AgentRunResult.Status.TIMED_OUT, result.join().status());
        pending.complete(null); runtime.runAll(); assertTrue(replies.isEmpty());
        assertEquals(0, result.join().interaction().orElseThrow().attempts().size());
    }

    /** 验证工具前守卫等待时取消，迟到允许不执行副作用或继续下一次模型。 */
    @Test void cancellingDelayedToolGuardPreventsLateSideEffect() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> checks.get() == 3 ? pending : CompletableFuture.completedStage(null));
        var cold = service.prepare(handle, request()); var result = cold.start().toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll(); assertEquals(0, toolCalls.get());
        cold.cancel(); runtime.runAll(); assertEquals(AgentRunResult.Status.CANCELLED, result.join().status());
        pending.complete(null); runtime.runAll(); assertEquals(0, toolCalls.get()); assertEquals(1, replies.size());
    }

    /** 验证工具校验超时按既有协议回填超时，后续模型可收束，迟到允许不执行副作用。 */
    @Test void toolGuardTimeoutPreventsLateSideEffect() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> checks.get() == 3 ? pending : CompletableFuture.completedStage(null));
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll(); assertFalse(result.isDone());
        runtime.advance(Duration.ofSeconds(16)); runtime.runAll();
        assertEquals(2, replies.size()); assertEquals(0, toolCalls.get()); assertFalse(result.isDone());
        pending.complete(null); runtime.runAll(); assertEquals(0, toolCalls.get()); assertEquals(2, replies.size());
        replies.getLast().complete(response(false)); runtime.runAll();
        assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        assertEquals(ToolInvocationResult.Status.TIMED_OUT, result.join().interaction().orElseThrow().tools().getFirst().status());
    }

    /** 验证最终引用校验未完成前不交付回答，拒绝后仍保留完成的模型诊断。 */
    @Test void finalDeliveryWaitsForReferenceVerification() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> checks.get() == 3 ? pending : CompletableFuture.completedStage(null));
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(false)); runtime.runAll(); assertFalse(result.isDone());
        pending.completeExceptionally(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED)); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); assertTrue(result.join().answer().isEmpty());
        assertTrue(result.join().interaction().isPresent());
    }

    /** 验证最终复核也受整轮时限控制，超时后迟到允许不能公开已完成的模型答案。 */
    @Test void finalVerificationTimeoutKeepsObservedDiagnostics() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> checks.get() == 3 ? pending : CompletableFuture.completedStage(null));
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(false)); runtime.runAll(); assertFalse(result.isDone());
        runtime.advance(Duration.ofSeconds(121)); runtime.runAll();
        assertEquals(AgentRunResult.Status.TIMED_OUT, result.join().status()); assertTrue(result.join().answer().isEmpty());
        assertTrue(result.join().interaction().isPresent());
        pending.complete(null); runtime.runAll(); assertTrue(result.join().answer().isEmpty()); assertEquals(1, replies.size());
    }

    /** 验证未登记 scope 在触碰持久化引用和模型之前被原有门控拒绝。 */
    @Test void unregisteredScopeNeverReadsMemoryOrModel() {
        policy.set(AgentScopePolicy.unregistered()); var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.SKIPPED, result.join().status()); verifyNoInteractions(references, models);
    }

    /** 验证错误适配器的跨 scope 引用不会成为当前组件或模型上下文。 */
    @Test void foreignReferenceIsRejectedBeforeModel() {
        resolve.set(ignored -> CompletableFuture.completedStage(new MemoryNamespaceReference(new AgentScope("另一客户端", "群"), MemoryNamespaceId.SHARED, 0, 0)));
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status()); assertNull(result.join().memoryReference()); verifyNoInteractions(models);
    }

    /** 验证引用端口停用和不可用分别映射为跳过与配置错误，不公开模型回答。 */
    @Test void referenceFailuresKeepTypedStatusAndHideAnswer() {
        for (var reason : List.of(AgentMemoryReferenceException.Reason.DISABLED, AgentMemoryReferenceException.Reason.UNAVAILABLE)) {
            resolve.set(ignored -> CompletableFuture.failedStage(new AgentMemoryReferenceException(reason)));
            var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
            assertEquals(reason == AgentMemoryReferenceException.Reason.DISABLED ? AgentRunResult.Status.SKIPPED : AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status());
            assertTrue(result.join().answer().isEmpty());
        }
        verifyNoInteractions(models);
    }

    /** 验证带会话 stamp 却缺少固定空间引用的请求在分配控制之前明确拒绝。 */
    @Test void conversationRequestsRequireFixedMemoryReference() {
        var original = request(); var stamp = new AgentConversationStamp(new AgentConversationKey(handle.id(), scope), UUID.randomUUID());
        var stamped = new AgentRunRequest(original.requestId(), original.actor(), original.activity(), original.messages(), stamp);
        var result = service.prepare(handle, stamped).start().toCompletableFuture().join();
        assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.status()); verifyNoInteractions(references, models);
    }

    /** 验证邮箱固定引用后、真实轮次启动前发生切换时拒绝，不重新解析或请求模型。 */
    @Test void referencedPreparationNeverRefreshesStaleMailboxBinding() {
        var input = request(); var stamp = new AgentConversationStamp(new AgentConversationKey(handle.id(), scope), UUID.randomUUID());
        var bound = new AgentRunRequest(input.requestId(), input.actor(), input.activity(), input.messages(), stamp, fixed);
        var cold = service.prepareReferenced(handle, bound, null);
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1));
        var result = cold.start().toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); verify(references, never()).resolve(any()); verifyNoInteractions(models);
    }

    /** 验证只调用同步守卫的旧交互端口明确失败，不能把异步空间校验当作已允许。 */
    @Test void synchronousOnlyInteractionPortCannotSkipAsyncGuard() {
        var port = mock(ILLMInteractionService.class);
        when(port.interact(any(), any(), any(), any(), any(ILLMInteractionGuard.class))).thenAnswer(call -> {
            ILLMInteractionGuard guard = call.getArgument(4); guard.check(call.getArgument(2)); throw new AssertionError("旧同步入口必须明确拒绝。");
        });
        try (var unsupported = new AgentService(gate, port, (definition, request, config) -> List.of(), new ObjectMapper(),
                runtime, runtime.scheduler, runtime.clock, logger, references)) {
            var result = unsupported.run(handle, request()).toCompletableFuture(); runtime.runAll();
            assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status()); verifyNoInteractions(models);
        }
    }
}
