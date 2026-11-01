package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.command.*;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryNamespaceBindingStore;
import org.zexnocs.teanekoagent.adapter.memory.StoreAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.AgentFactory;
import org.zexnocs.teanekoagent.agent.AgentRegistry;
import org.zexnocs.teanekoagent.agent.AgentService;
import org.zexnocs.teanekoagent.agent.access.*;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationOptions;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationService;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationSettings;
import org.zexnocs.teanekoagent.agent.conversation.AgentQueueSettings;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.command.TeaNekoCommandErrorHandler;
import org.zexnocs.teanekoapp.command.TeaNekoHelpSubCommandHandler;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.*;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.event.EventService;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用事务与 FakeClient，验证真实 Agent/命令往返、原权限、持久化防重及基本管理闭环。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(30)
class AgentCommandIntegrationTest {
    /**
     * 受控原生命令，业务由用例完成；非静态测试类不参与应用自动扫描。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command(value = "/agent-test", permission = CommandPermission.OWNER, scope = CommandScope.ALL)
    public class Handler {
        final CompletableFuture<String> business = new CompletableFuture<>();
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        volatile CommandData<ITeaNekoMessageData> received;
        /**
         * 保留原始类型、身份和父控制，返回真实未完成业务。
         * @param data 原权限链交付的命令
         * @return 由测试完成的实际业务阶段
         */
        @DefaultCommand public CompletionStage<String> execute(CommandData<ITeaNekoMessageData> data) { received = data; calls.incrementAndGet(); started.countDown(); return business; }
    }
    /**
     * 实际扫描、分发、权限、执行、事件和 Agent 管线；只替换任务调度、scope 覆盖存储和供应商传输。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2);
        final ILogger logger = mock(ILogger.class);
        final FakeClient client = new FakeClient(logger);
        final AgentRegistry registry = new AgentRegistry();
        final AgentScopeConfigData config = new AgentScopeConfigData();
        final Handler handler = new Handler();
        final AtomicReference<String> command = new AtomicReference<>("/agent-test");
        final List<ILLMPrompt> prompts = new CopyOnWriteArrayList<>();
        final TeaNekoCommandConverter converter = new TeaNekoCommandConverter(mock(TeaNekoScopeService.class));
        final EasyDataCommandAttemptStore attempts = new EasyDataCommandAttemptStore(db.repository, db.database, db.mapper);
        final MemoryNamespaceRegistry namespaces = new MemoryNamespaceRegistry(db.store, () -> 1, Clock.systemUTC());
        final EasyDataMemoryNamespaceBindingStore bindingStore = new EasyDataMemoryNamespaceBindingStore(db.store);
        final MemoryNamespaceBindings bindings = new MemoryNamespaceBindings(bindingStore, () -> 1, Clock.systemUTC());
        final StoreAgentMemoryReferences references;
        final CommandDispatcher dispatcher;
        final TeaNekoHelpSubCommandHandler help;
        final NativeCommandBridge bridge;
        final AgentCommandToolSource tools;
        final AgentService turns;
        final AgentConversationService mailbox;
        final AgentCommandGateway gateway;
        /** 复用原任务服务替身模式，实际处理方法返回 Stage 时不阻塞任务线程。 */
        @SuppressWarnings({"rawtypes", "unchecked"})
        Harness() {
            config.setCommandToolsEnabled(true);
            IAgentScopePolicyPort scopes = ignored -> new AgentScopePolicy(true, config.isEnabled(), AgentScopePolicy.Mode.ACTIVE);
            references = new StoreAgentMemoryReferences(scopes, bindingStore, namespaces, workers);
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(1000).build()), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, scopes, ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), logger);
            var personalities = mock(IAgentPersonalitySource.class);
            when(personalities.resolve(any(), any())).thenReturn(CompletableFuture.completedStage(new AgentPersonalitySnapshot("人格", "teaneko", 1, "补正", 0, "基础人格", "", null)));
            when(personalities.verify(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
            var tasks = mock(ITaskService.class, CALLS_REAL_METHODS);
            doAnswer(call -> {
                ITaskConfig<?> task = call.getArgument(1); var result = new CompletableFuture<ITaskResult<?>>();
                try { result.complete(task.getCallable().call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
                return new TaskFuture(logger, task.getName(), result);
            }).when(tasks).subscribeWithFuture(any(UUID.class), any(ITaskConfig.class), any(Class.class));
            var beans = mock(IBeanScanner.class); var entries = new HashMap<String, org.zexnocs.teanekocore.framework.pair.Pair<Command, Object>>();
            doReturn(entries).when(beans).getBeansWithAnnotation(Command.class); when(beans.getBeanClass(any())).thenAnswer(call -> call.getArgument(0).getClass());
            var scanner = new CommandScanner(logger, beans); var eventScanner = mock(EventHandlerScanner.class);
            doReturn(List.of()).when(eventScanner).getEventHandlerList(any());
            var scopeManager = mock(ICommandScopeManager.class);
            when(scopeManager.inScope(any(Command.class), any(DefaultCommand.class), any())).thenReturn(true);
            when(scopeManager.inScope(any(Command.class), any(SubCommand.class), any())).thenReturn(true);
            help = new TeaNekoHelpSubCommandHandler(logger, mock(CommandDescriptionScanner.class), new CommandPermissionManager(), scopeManager);
            dispatcher = new CommandDispatcher(logger, new CommandPermissionManager(), scopeManager, new CommandExecutor(tasks), new CommandArgumentProcessor(logger), scanner, new EventService(logger, eventScanner, tasks));
            var configs = mock(IConfigDataService.class); when(configs.getConfigData(any(), eq(AgentScopeConfigData.class), anyString())).thenAnswer(call -> Optional.of(config));
            bridge = new NativeCommandBridge(converter, dispatcher, new TeaNekoCommandErrorHandler(logger), help, attempts, workers,
                    gate, registry, references, personalities, configs, new AgentScopeConfigManager());
            tools = new AgentCommandToolSource(bridge, configs, new AgentScopeConfigManager(), db.mapper);
            var models = mock(ILLMModelService.class);
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                ILLMPrompt prompt = call.getArgument(0); prompts.add(prompt);
                var response = prompt.getMessages().getLast().getRole() == LLMMessageRole.TOOL || prompt.getOptions().getTools().isEmpty()
                        ? LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("已收到命令处理回执").build()).build()
                        : LLMAssistantMessage.builder().contents(List.of()).toolCalls(List.of(LLMToolCall.builder().id("原调用-" + prompts.size())
                                .name(AgentCommandToolSource.NAME).arguments(db.mapper.writeValueAsString(Map.of("command", command.get()))).build())).build();
                return new TaskFuture<>(logger, "离线命令模型", CompletableFuture.completedFuture(LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build())
                        .choices(List.of(LLMChoice.builder().index(0).finishReason(response.getToolCalls().isEmpty() ? "stop" : "tool_calls").message(response).build())).build()));
            });
            turns = new AgentService(gate, new LLMInteractionService(models, LLMInteractionSettings::defaults, workers, timers),
                    (definition, request, actual) -> tools.select(definition, request), db.mapper, workers, timers, Clock.systemUTC(), logger, references, IAgentPromptSource.empty(), personalities);
            mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults, workers, timers, Clock.systemUTC(), logger, AgentConversationSettings::defaults,
                    (definition, request) -> AgentConversationOptions.defaults(), db.mapper, references, personalities);
            gateway = new AgentCommandGateway(new AgentFactory(registry), registry, mailbox, mailbox, scopes, bridge, references, bindings, namespaces, () -> 1, timers, attempts);
            for (var bean : List.of(handler, new AgentCommand(gateway))) { entries.put(bean.getClass().getName(), HashPair.of(bean.getClass().getAnnotation(Command.class), bean)); }
            scanner.init();
        }
        /**
         * 取得与真实角色一致的原生群消息，沿用 FakeClient 的稳定 UUID/作用域规则。
         * @param user 用户平台来源
         * @param role 真实角色
         * @param text 原文本
         * @return 实际原生消息对象
         */
        ITeaNekoMessageData source(String user, CommandPermission role, String text) { return client.handleGroupMessage("群", user, "用户", role, text).getData(); }
        /** 关闭拥有者及借用设施，原业务可在取消后结束，不阻塞用例关闭。 */
        @Override public void close() { handler.business.complete("关闭验证"); gateway.close(); mailbox.close(); turns.close(); registry.close(); timers.shutdownNow(); workers.close(); db.close(); }
    }
    /**
     * 验证真实模型到原命令的异步闭环、原生类型及父控制、最终发送和入口去重。
     * @throws Exception 当异步流程未按时完成时
     */
    @Test void realToolDispatchWaitsForBusinessAndPreservesNativeIdentity() throws Exception {
        try (var h = new Harness()) {
            var source = h.source("甲", CommandPermission.OWNER, "/agent chat 请执行  指令");
            var stage = h.dispatcher.dispatch(h.converter.__parse(source), new TeaNekoCommandErrorHandler(h.logger), h.help);
            assertTrue(h.handler.started.await(5, TimeUnit.SECONDS));
            assertFalse(stage.toCompletableFuture().isDone()); assertSame(source, h.handler.received.getRawData());
            assertEquals(source.getClient().getClass(), h.handler.received.getClientClass()); assertEquals(CommandPermission.OWNER, h.handler.received.getPermission());
            assertNotNull(h.handler.received.getParentExecution()); assertEquals(source.getUserData().getUuid().toString(), h.handler.received.getSenderId());
            h.handler.business.complete("实际业务值不直接暴露模型"); stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(2, h.prompts.size()); assertTrue(h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains("COMPLETED"));
            assertTrue(h.db.mapper.writeValueAsString(h.prompts.getFirst().getMessages()).contains("请执行  指令"));
            assertFalse(h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains("实际业务值不直接暴露模型"));
            int sent = h.client.getSentData().size(); h.gateway.chat(source, "请执行  指令", null).toCompletableFuture().join();
            assertEquals(1, h.handler.calls.get()); assertEquals(sent, h.client.getSentData().size());
            try (var rebuilt = new AgentCommandGateway(new AgentFactory(h.registry), h.registry, h.mailbox, h.mailbox,
                    ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE), h.bridge, h.references, h.bindings, h.namespaces, () -> 1, h.timers, h.attempts)) {
                rebuilt.chat(source, "请执行  指令", null).toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals(2, h.prompts.size()); assertEquals(1, h.handler.calls.get());
            }
        }
    }
    /**
     * 验证原角色拒绝不会调用处理方法，未开放工具和关闭 scope 均不能绕过。
     * @throws Exception 当异步流程未按时完成时
     */
    @Test void realPermissionAndScopeFlagsRemainEffective() throws Exception {
        try (var h = new Harness()) {
            var source = h.source("成员", CommandPermission.MEMBER, "/agent chat 请求执行");
            h.gateway.chat(source, "请求执行", null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(0, h.handler.calls.get()); assertTrue(h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains("PERMISSION_DENIED"));
            h.config.setCommandToolsEnabled(false);
            h.gateway.chat(h.source("群主", CommandPermission.OWNER, "/agent chat 新请求"), "新请求", null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(h.prompts.getLast().getOptions().getTools().isEmpty());
            int models = h.prompts.size(); h.config.setEnabled(false);
            h.gateway.chat(h.source("群主", CommandPermission.OWNER, "/agent chat 已关闭"), "已关闭", null).toCompletableFuture().join();
            assertEquals(models, h.prompts.size());
        }
    }
    /** 验证原事务唯一执行权、服务重建后的未知在途、参数冲突和持久化终态，不重复副作用。 */
    @Test void durableAttemptsDoNotReexecuteAfterRebuildOrConflict() {
        try (var h = new Harness()) {
            var actor = NativeCommandBridge.actor(h.source("甲", CommandPermission.OWNER, "来源"));
            var reference = h.references.resolve(actor.scope()).toCompletableFuture().join();
            var attempt = new ICommandAttemptStore.Attempt(UUID.randomUUID(), "调用", actor, reference, StableDigest.sha256("/agent-test"));
            var a = h.attempts.claim(attempt); var b = h.attempts.claim(attempt);
            assertNotEquals(a.toCompletableFuture().join().acquired(), b.toCompletableFuture().join().acquired());
            var rebuilt = new EasyDataCommandAttemptStore(h.db.repository, h.db.database, h.db.mapper);
            assertEquals(CommandInvocationReceipt.Status.INDETERMINATE, rebuilt.claim(attempt).toCompletableFuture().join().receipt().status());
            var changed = new ICommandAttemptStore.Attempt(attempt.requestId(), attempt.callId(), actor, reference, StableDigest.sha256("不同参数"));
            assertEquals(CommandInvocationReceipt.Status.CONFLICT, rebuilt.claim(changed).toCompletableFuture().join().receipt().status());
            var done = new CommandInvocationReceipt(CommandInvocationReceipt.Status.COMPLETED); rebuilt.finish(attempt, done).toCompletableFuture().join();
            assertEquals(done, h.attempts.claim(attempt).toCompletableFuture().join().receipt());
        }
    }
    /**
     * 验证经原分发调用 Agent 时拒绝等待自身邮箱，普通用户不能停止他人请求，停止后不重复执行业务。
     * @throws Exception 当异步流程未按时完成时
     */
    @Test void recursiveCommandAndStoppingOtherUsersCannotDeadlockOrStealOwnership() throws Exception {
        try (var h = new Harness()) {
            h.command.set("/agent chat 递归请求");
            h.gateway.chat(h.source("甲", CommandPermission.OWNER, "/agent chat 起点"), "起点", null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(2, h.prompts.size()); assertTrue(h.client.getSentRawMessages().stream().anyMatch(text -> text.contains("REENTRANT")));
            h.command.set("/agent-test"); var source = h.source("甲", CommandPermission.OWNER, "/agent chat 等待业务");
            var pending = h.gateway.chat(source, "等待业务", null); assertTrue(h.handler.started.await(5, TimeUnit.SECONDS));
            var status = h.gateway.status(source).toCompletableFuture().join(); var matcher = Pattern.compile("[0-9a-f]{8}-[0-9a-f-]{27}").matcher(status); assertTrue(matcher.find());
            var id = matcher.group(); assertTrue(h.gateway.stop(h.source("乙", CommandPermission.MEMBER, "停止"), id).contains("NOT_OWNER"));
            assertTrue(h.gateway.stop(source, id).contains("REQUESTED")); pending.toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(1, h.handler.calls.get()); h.handler.business.complete("取消后真实完成");
        }
    }
    /**
     * 验证基本管理经过原 OWNER 拒绝、事务配额、共享绑定、列表及当前引用同步。
     * @throws Exception 当异步流程未按时完成时
     */
    @Test void namespaceManagementUsesRealOwnerAndAtomicBindingContracts() throws Exception {
        try (var h = new Harness()) {
            var member = h.source("成员", CommandPermission.MEMBER, "/agent reset");
            assertEquals(CommandResult.Status.PERMISSION_DENIED, h.dispatcher.dispatch(h.converter.__parse(member), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().get(10, TimeUnit.SECONDS).status());
            var owner = h.source("甲", CommandPermission.OWNER, "/agent reset");
            assertTrue(h.gateway.changeNamespace(owner, null, null).toCompletableFuture().get(10, TimeUnit.SECONDS).contains("已提交"));
            var actor = NativeCommandBridge.actor(owner); var target = h.bindings.readReference(actor.scope()).toCompletableFuture().join().orElseThrow().namespaceId();
            assertNotEquals(MemoryNamespaceId.SHARED, target); assertTrue(h.gateway.namespaces(owner, 0).toCompletableFuture().join().contains(target.value()));
            assertTrue(h.gateway.changeNamespace(h.source("甲", CommandPermission.OWNER, "再次 reset"), null, null).toCompletableFuture().join().contains("QUOTA_EXCEEDED"));
            var other = h.client.handleGroupMessage("另一群", "乙", "用户", CommandPermission.OWNER, "绑定").getData();
            assertTrue(h.gateway.changeNamespace(other, target.value(), null).toCompletableFuture().join().contains("已提交"));
            assertEquals(target, h.bindings.readReference(NativeCommandBridge.actor(other).scope()).toCompletableFuture().join().orElseThrow().namespaceId());
        }
    }
}
