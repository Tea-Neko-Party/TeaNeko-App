package org.zexnocs.teanekoagent.adapter.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent._app_config.MemoryToolConfig;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.memory.access.*;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.memory.profile.UserProfileRequest;
import org.zexnocs.teanekoagent.memory.profile.UserProfileView;
import org.zexnocs.teanekoagent.memory.recall.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 通过真实固定工具集校验记忆工具 schema、原始授权、逐动作身份、完整信封及默认装配。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class AgentMemoryToolSourceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final AgentActorContext actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "平台用户", "原消息", "目标");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final AgentRunRequest request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE, List.of(), null, reference, new AgentUserText("原消息", "记住：我喜欢茶"));
    private final AgentDefinition definition = new AgentDefinition("工具测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
    private final AgentRuntimeSnapshot runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(null), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
    private final MemoryAccessService access = mock(MemoryAccessService.class);
    private final AtomicReference<MemoryAccessPolicy> policy = new AtomicReference<>(new MemoryAccessPolicy(true, true, true, false));
    private final AtomicReference<MemoryRecallSettings> defaults = new AtomicReference<>(MemoryRecallSettings.defaults());

    /**
     * 构造显式工具源，测试直接拥有同步业务域。
     * @return 不执行数据库工作的工具适配器
     */
    private AgentMemoryToolSource source() { return new AgentMemoryToolSource(access, ignored -> policy.get(), defaults::get, mapper, Runnable::run); }

    /**
     * 建立与实际 Agent 一致的可信组件，不从业务参数取得身份。
     * @param input 原始宿主请求
     * @param control 借用测试控制
     * @return 完整可信工具上下文
     */
    private ToolInvocationContext trusted(AgentRunRequest input, ExecutionControl control) {
        var entity = new Entity(EntityId.random(), ComponentPatch.builder().put(AgentComponents.REQUEST, input).put(AgentComponents.ACTOR, input.actor())
                .put(AgentComponents.MEMORY_REFERENCE, Objects.requireNonNull(input.memoryReference())).build());
        return new ToolInvocationContext(input.requestId(), entity.snapshot(), control);
    }

    /**
     * 通过真实参数校验及白名单执行一个记忆工具。
     * @param name 固定工具名称
     * @param arguments 模型业务参数
     * @param control 借用控制
     * @return 实际工具结果
     */
    private ToolInvocationResult execute(String name, String arguments, ExecutionControl control) {
        var set = new LLMToolSet(mapper, source().select(definition, request, runtime));
        return set.execute(LLMToolCall.builder().id("原调用一").name(name).arguments(arguments).build(), trusted(request, control)).toCompletableFuture().join();
    }

    /**
     * 构造明确部分覆盖的空结果，不能声称确定无资料。
     * @return 保留扫描限制的结果
     */
    private MemoryRecallResult partial() { return new MemoryRecallResult(reference, now, List.of(), 5, 0, Set.of(MemoryRecallResult.Limit.SCAN), 2); }

    /** 验证缺失原文或只有消息历史时只选择读取，只有真实原文才选入新增；无引用模式为空。 */
    @Test void selectionRequiresSeparateOriginalTextAndFixedReference() {
        var source = source(); var without = new AgentRunRequest(request.requestId(), actor, request.activity(), request.messages(), null, reference);
        assertEquals(List.of(AgentMemoryToolSource.QUERY, AgentMemoryToolSource.PROFILE), source.select(definition, without, runtime).stream().map(binding -> binding.definition().getName()).toList());
        assertEquals(3, source.select(definition, request, runtime).size());
        assertTrue(source.select(definition, new AgentRunRequest(request.requestId(), actor, request.activity(), List.of()), runtime).isEmpty());
        var noTools = new AgentDefinition("无工具", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.SERVICE));
        assertTrue(source.select(noTools, request, runtime).isEmpty()); verifyNoInteractions(access);
    }

    /** 验证独立开关控制可见白名单，学习不能自行开放新增或恢复关闭读取。 */
    @Test void switchesSelectOnlyExplicitCapabilities() {
        policy.set(new MemoryAccessPolicy(true, false, true, true));
        assertEquals(List.of(AgentMemoryToolSource.ADD), source().select(definition, request, runtime).stream().map(binding -> binding.definition().getName()).toList());
        policy.set(new MemoryAccessPolicy(true, true, false, true)); assertEquals(2, source().select(definition, request, runtime).size());
        policy.set(new MemoryAccessPolicy(false, true, true, true)); assertTrue(source().select(definition, request, runtime).isEmpty());
    }

    /** 验证模型提交 namespace、actor、正文或授权字段会在真实工具集被拒绝，不触发业务。 */
    @Test void schemasRejectForgedIdentityAuthorizationAndWriteBody() {
        try (var control = ExecutionControl.create()) {
            for (var arguments : List.of("{\"kind\":\"FACT\",\"namespace\":\"other\"}", "{\"kind\":\"FACT\",\"actor\":{}}",
                    "{\"kind\":\"FACT\",\"authorized\":true}", "{\"kind\":\"FACT\",\"text\":\"其他事实\"}", "{\"kind\":\"FACT\",\"actionId\":\"伪造\"}",
                    "{\"kind\":\"RELATIONSHIP\"}", "{\"kind\":\"FACT\",\"kind\":\"PREFERENCE\"}")) {
                assertEquals(ToolInvocationResult.Status.INVALID_ARGUMENTS, execute(AgentMemoryToolSource.ADD, arguments, control).status());
            }
            assertEquals(ToolInvocationResult.Status.INVALID_ARGUMENTS, execute(AgentMemoryToolSource.QUERY, "{\"scopeId\":\"其他群\"}", control).status());
            assertEquals(ToolInvocationResult.Status.NOT_ALLOWED, execute("memory_clear", "{}", control).status()); verifyNoInteractions(access);
        }
    }

    /** 验证实际追加保留真实原文及原调用 ID，模型只选择类别，不生成来源身份或稳定操作 ID。 */
    @Test void appendUsesOriginalBodyAndAcceptedToolCallId() {
        var stored = new MemoryAddResult(MemoryFactReceipt.Status.STORED, UUID.randomUUID());
        when(access.append(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(stored));
        try (var control = ExecutionControl.create()) {
            var result = execute(AgentMemoryToolSource.ADD, "{\"kind\":\"PREFERENCE\"}", control);
            assertEquals(ToolInvocationResult.Status.SUCCESS, result.status()); assertTrue(mapper.readTree(result.output()).path("ok").booleanValue());
            var context = ArgumentCaptor.forClass(MemoryAccessContext.class); var action = ArgumentCaptor.forClass(String.class);
            var intent = ArgumentCaptor.forClass(MemoryAddIntent.class); var value = ArgumentCaptor.forClass(MemoryFactValue.class);
            verify(access).append(context.capture(), action.capture(), intent.capture(), value.capture(), same(control));
            assertEquals(request.requestId(), context.getValue().requestId()); assertEquals(actor, context.getValue().actor()); assertEquals(reference, context.getValue().reference());
            assertEquals("原调用一", action.getValue()); assertEquals("我喜欢茶", intent.getValue().text()); assertEquals(intent.getValue().text(), value.getValue().text());
            assertEquals(MemoryFactValue.Kind.PREFERENCE, value.getValue().kind()); assertEquals(MemoryEventTime.unknown(), value.getValue().eventTime());
            assertFalse(result.output().contains("原消息")); assertFalse(result.output().contains("我喜欢茶"));
            when(access.append(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(new MemoryAddResult(MemoryFactReceipt.Status.CONFLICT, null)));
            var conflict = mapper.readTree(execute(AgentMemoryToolSource.ADD, "{\"kind\":\"FACT\"}", control).output());
            assertFalse(conflict.path("ok").booleanValue()); assertEquals("OPERATION_CONFLICT", conflict.path("error").path("code").stringValue());
        }
    }

    /** 验证调用标识超出既有来源合同则明确拒绝，不截断后与其他动作碰撞。 */
    @Test void oversizedOriginalCallIdCannotStartAppend() {
        try (var control = ExecutionControl.create()) {
            var set = new LLMToolSet(mapper, source().select(definition, request, runtime));
            var result = set.execute(LLMToolCall.builder().id("动".repeat(129)).name(AgentMemoryToolSource.ADD).arguments("{\"kind\":\"FACT\"}").build(), trusted(request, control)).toCompletableFuture().join();
            assertEquals("INVALID_CALL", mapper.readTree(result.output()).path("error").path("code").stringValue()); verifyNoInteractions(access);
        }
    }

    /** 验证查询保留显式主语、关键词、类型和时间估算，不增加来源隔离且预算只能收紧。 */
    @Test void queryRetainsDomainFiltersAndLocalBudgets() {
        when(access.recall(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(partial()));
        defaults.set(new MemoryRecallSettings(3, 2, 4, 16000, 300)); var other = UUID.randomUUID();
        try (var control = ExecutionControl.create()) {
            var result = execute(AgentMemoryToolSource.QUERY, "{\"subjectId\":\"" + other + "\",\"keywords\":[\"茶\"],\"tags\":[\"饮品\"],\"kinds\":[\"PREFERENCE\"],\"start\":\"2026-10-28T00:00:00Z\",\"includeEstimated\":false,\"limit\":20,\"maxScanned\":1000}", control);
            var query = ArgumentCaptor.forClass(MemoryRecallQuery.class); var budget = ArgumentCaptor.forClass(MemoryRecallLimits.class);
            verify(access).recall(any(), query.capture(), eq(MemoryRecallRequest.Use.TOOL), budget.capture(), same(control));
            assertEquals(other, query.getValue().subjectId()); assertNull(query.getValue().sourceScope()); assertEquals(List.of("茶"), query.getValue().keywords());
            assertEquals(Set.of(MemoryFactValue.Kind.PREFERENCE), query.getValue().kinds()); assertFalse(query.getValue().includeEstimated()); assertNotNull(query.getValue().start());
            assertEquals(new MemoryRecallLimits(4, MemoryToolReplies.EVIDENCE_CHARACTERS, 300), budget.getValue());
            assertFalse(result.truncated()); assertFalse(mapper.readTree(result.output()).path("data").path("definitivelyEmpty").booleanValue());
        }
    }

    /** 验证默认工具条数来自本地策略，省略查询主体不自动限定当前用户。 */
    @Test void defaultQueryUsesConfiguredToolCountWithoutImplicitSubject() {
        defaults.set(new MemoryRecallSettings(3, 2, 4, 5000, 100)); when(access.recall(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(partial()));
        try (var control = ExecutionControl.create()) {
            execute(AgentMemoryToolSource.QUERY, "{}", control); var query = ArgumentCaptor.forClass(MemoryRecallQuery.class); var limits = ArgumentCaptor.forClass(MemoryRecallLimits.class);
            verify(access).recall(any(), query.capture(), any(), limits.capture(), any()); assertNull(query.getValue().subjectId()); assertEquals(new MemoryRecallLimits(2, 5000, 100), limits.getValue());
        }
    }

    /** 验证画像默认真实用户，显式其他 UUID 仅影响读取，返回证据不重复分类输出。 */
    @Test void profileDefaultsToRealActorAndAllowsExplicitSharedSubject() {
        when(access.profile(any(), any(), any(), any(), any())).thenAnswer(call -> {
            var profile = new UserProfileRequest(reference, call.getArgument(1), MemoryRecallRequest.Use.TOOL, null);
            return CompletableFuture.completedFuture(UserProfileView.from(profile, partial()));
        });
        try (var control = ExecutionControl.create()) {
            var self = mapper.readTree(execute(AgentMemoryToolSource.PROFILE, "{}", control).output()).path("data");
            assertEquals(actor.userId().orElseThrow().toString(), self.path("subjectId").stringValue()); assertFalse(self.has("facts")); assertFalse(self.has("preferences"));
            var other = UUID.randomUUID(); var different = mapper.readTree(execute(AgentMemoryToolSource.PROFILE, "{\"subjectId\":\"" + other + "\"}", control).output());
            assertEquals(other.toString(), different.path("data").path("subjectId").stringValue()); assertFalse(different.path("data").path("definitivelyEmpty").booleanValue());
        }
    }

    /** 验证领域参数越界、无时区日期和整数溢出在业务调用前拒绝，不伪装空记忆。 */
    @Test void invalidDomainArgumentsNeverReachAccessService() {
        try (var control = ExecutionControl.create()) {
            for (var arguments : List.of("{\"start\":\"昨天\"}", "{\"subjectId\":\"平台用户\"}", "{\"maxScanned\":0}", "{\"limit\":2147483648}",
                    "{\"keywords\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]}")) {
                assertEquals("INVALID_ARGUMENTS", mapper.readTree(execute(AgentMemoryToolSource.QUERY, arguments, control).output()).path("error").path("code").stringValue());
            }
            verifyNoInteractions(access);
        }
    }

    /** 验证绑定不能跨请求或来源交接，不因相同空间就接受另一个用户的工具上下文。 */
    @Test void selectedBindingsRejectForeignTrustedRequest() {
        var set = new LLMToolSet(mapper, source().select(definition, request, runtime));
        var other = new AgentRunRequest(UUID.randomUUID(), actor, request.activity(), List.of(), null, reference);
        try (var control = ExecutionControl.create()) {
            var result = set.execute(LLMToolCall.builder().id("原调用").name(AgentMemoryToolSource.QUERY).arguments("{}").build(), trusted(other, control)).toCompletableFuture().join();
            assertEquals("UNAVAILABLE", mapper.readTree(result.output()).path("error").path("code").stringValue()); verifyNoInteractions(access);
        }
    }

    /** 验证错误类别不丢失，原始异常文本、附加异常及原文不进入业务错误信封。 */
    @Test void knownFailuresProduceSafeFixedBusinessCodes() {
        var failure = new MemoryAccessException(MemoryAccessException.Reason.READ_DISABLED); failure.addSuppressed(new IllegalStateException("敏感原文"));
        when(access.recall(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.failedFuture(failure));
        try (var control = ExecutionControl.create()) {
            var result = execute(AgentMemoryToolSource.QUERY, "{}", control); assertFalse(result.output().contains("敏感"));
            assertEquals("READ_DISABLED", mapper.readTree(result.output()).path("error").path("code").stringValue());
        }
    }

    /** 验证真实提交等待不提前返回成功，阶段取消保持原工具调用配对且不取消原业务 Future。 */
    @Test void pendingAppendAndCancellationKeepCompletionContract() {
        var pending = new CompletableFuture<MemoryAddResult>(); when(access.append(any(), any(), any(), any(), any())).thenReturn(pending);
        try (var control = ExecutionControl.create()) {
            var set = new LLMToolSet(mapper, source().select(definition, request, runtime)); var call = LLMToolCall.builder().id("原调用").name(AgentMemoryToolSource.ADD).arguments("{\"kind\":\"FACT\"}").build();
            var result = set.execute(call, trusted(request, control)).toCompletableFuture(); assertFalse(result.isDone()); control.cancel();
            assertEquals(ToolInvocationResult.Status.CANCELLED, result.join().status()); assertEquals("原调用", result.join().call().getId()); assertFalse(pending.isCancelled());
        }
    }

    /** 验证完整信封使用紧凑 JSON 且不会被通用工具结果按字符切碎；超大错误来源明确失败。 */
    @Test void envelopesRemainValidAndOversizedSourcesFailExplicitly() {
        var pretty = mapper.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build(); var replies = new MemoryToolReplies(pretty);
        var record = new MemoryFactRecord(UUID.randomUUID(), MemoryNamespaceId.SHARED, 0, 0, actor.userId().orElseThrow(),
                new MemoryFactValue(MemoryFactValue.Kind.FACT, "\"".repeat(4096), List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null),
                new MemoryFactSource(request.requestId(), "动作", actor), now, now, false);
        var evidence = List.of(MemoryRecallEvidence.from(record));
        var tooLarge = new MemoryRecallResult(reference, now, evidence, 1, 1, Set.of(), mapper.writeValueAsString(evidence).length());
        var encoded = replies.recall(tooLarge); assertEquals("RESULT_TOO_LARGE", mapper.readTree(encoded).path("error").path("code").stringValue());
        var valid = replies.recall(partial()); assertFalse(valid.contains("\n")); assertTrue(valid.length() < LLMToolSet.DEFAULT_RESULT_CHARACTER_LIMIT);
        assertFalse(mapper.readTree(valid).path("data").path("definitivelyEmpty").booleanValue());
    }

    /** 验证默认装配真实注册一个工具源，自定义策略可替换，构造不访问配置或记忆。 */
    @Test void defaultSpringSourceIsLazyAndCanBeReplaced() {
        var policies = mock(IMemoryAccessPolicy.class); var budgets = mock(IMemoryRecallDefaults.class);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("memoryAccess", access); context.getBeanFactory().registerSingleton("memoryPolicy", policies);
            context.getBeanFactory().registerSingleton("memoryDefaults", budgets); context.getBeanFactory().registerSingleton("jsonMapper", mapper);
            context.getBeanFactory().registerSingleton("virtualExecutor", executor); context.register(MemoryToolConfig.class); context.refresh();
            assertInstanceOf(AgentMemoryToolSource.class, context.getBean(IAgentToolSource.class)); verifyNoInteractions(access, policies, budgets); assertFalse(executor.isShutdown());
        }
        try (var context = new AnnotationConfigApplicationContext()) {
            IAgentToolSource custom = (definition, request, runtime) -> List.of(); context.registerBean("customTools", IAgentToolSource.class, () -> custom);
            context.register(MemoryToolConfig.class); context.refresh(); assertSame(custom, context.getBean(IAgentToolSource.class)); assertEquals(1, context.getBeansOfType(IAgentToolSource.class).size());
        }
    }
}
