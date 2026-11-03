package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.adapter.command.*;
import org.zexnocs.teanekoagent.adapter.config.AgentNamespaceConfigFields;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.history.EasyDataHistoryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryManagementStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryNamespaceBindingStore;
import org.zexnocs.teanekoagent.adapter.memory.StoreAgentMemoryReferences;
import org.zexnocs.teanekoagent.adapter.personality.ConfiguredPersonalities;
import org.zexnocs.teanekoagent.adapter.personality.EasyDataPersonalityStore;
import org.zexnocs.teanekoagent.adapter.platform.*;
import org.zexnocs.teanekoagent.adapter.skill.AgentSkills;
import org.zexnocs.teanekoagent.adapter.skill.ConfiguredSkillPolicy;
import org.zexnocs.teanekoagent.agent.AgentFactory;
import org.zexnocs.teanekoagent.agent.AgentRegistry;
import org.zexnocs.teanekoagent.agent.AgentService;
import org.zexnocs.teanekoagent.agent.access.*;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.agent.conversation.*;
import org.zexnocs.teanekoagent.agent.monitor.AgentRuntimeMonitor;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.agent.trigger.AgentTriggerGate;
import org.zexnocs.teanekoagent.file_config.personality.AgentPersonalityFileConfig;
import org.zexnocs.teanekoagent.history.*;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactAppend;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactSource;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.personality.PersonalityService;
import org.zexnocs.teanekoagent.skill.ISkillCatalog;
import org.zexnocs.teanekoagent.skill.SkillCatalogSnapshot;
import org.zexnocs.teanekoagent.skill.SkillDefinition;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.command.TeaNekoCommandErrorHandler;
import org.zexnocs.teanekoapp.command.TeaNekoHelpSubCommandHandler;
import org.zexnocs.teanekoapp.config.TeaNekoConfigCommand;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.command.*;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataQueryService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManagerScanner;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.event.EventService;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用事务与 FakeClient，验证真实 Agent/命令往返、原权限、持久化防重及基本管理闭环。
 * <br>2026/11/02：验证无提及私聊和独立模型元数据，保留图文顺序与平台回复。
 * <br>2026/11/03：原命令、自动和自主发送集成逐段持久回执。
 * <br>2026/11/03：覆盖拟人编译、语义历史及双原生入口交付。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Timeout(30)
class AgentCommandIntegrationTest {
    /**
     * 受控原生命令，业务由用例完成；非静态测试类不参与应用自动扫描。
     * @author zExNocs
     * @date 2026/11/02
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
     * <br>2026/11/03：加入真实专用数据发送账本，复用原发送、失败和取消用例。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.2-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        // 2026/11/03：原入口集成用例同时验证实际平台发送账本，不另造网关夹具。
        final org.zexnocs.teanekoagent.history.AgentDeliveryService delivery = new org.zexnocs.teanekoagent.history.AgentDeliveryService(
                new org.zexnocs.teanekoagent.adapter.memory.EasyDataDeliveryStore(db.data, db.store, db.mapper), Runnable::run, Clock.systemUTC(), () -> Duration.ofDays(7));
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2);
        final ILogger logger = mock(ILogger.class);
        final FakeClient client = new FakeClient(logger);
        final AgentRegistry registry = new AgentRegistry();
        final AgentTriggerGate triggers = new AgentTriggerGate(scope -> new AgentTriggerGate.Settings(
                this.registered.get() ? this.config.toPolicy() : AgentScopePolicy.unregistered(), this.config.getActivityTimeZone(),
                this.config.getProactiveCooldownSeconds(), this.config.getProactiveQuietStartHour(), this.config.getProactiveQuietEndHour()), Clock.systemUTC());
        final AgentAutomaticAccess automaticAccess = new AgentAutomaticAccess(triggers);
        final AtomicReference<String> automaticAnswer = new AtomicReference<>();
        // 2026/11/03：仅新表达闭环用例启用，复用原事务、模型和发送夹具。
        final @org.jspecify.annotations.Nullable ExpressionHarness expression;
        /**
         * 当前测试拥有的局部表达容器与队列，不引入新的数据库替身。
         * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
         *
         * @author zExNocs
         * @date 2026/11/03
         * @since 4.0.0
         * @version 4.6.2-l
         */
        private final class ExpressionHarness implements AutoCloseable {
            final org.springframework.context.annotation.AnnotationConfigApplicationContext beans = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
            final org.zexnocs.teanekoagent.expression.ExpressionRuntime runtime;
            final org.zexnocs.teanekoagent.expression.ReplyCoordinator replies;
            final org.zexnocs.teanekoagent.expression.ExpressionDelivery sending;
            /** 仅注册真实协议类，配置和实际回执继续借用原夹具。 */
            ExpressionHarness() {
                beans.register(org.zexnocs.teanekoagent.expression.ExpressionReplyProtocol.class, org.zexnocs.teanekoagent.expression.TextReplyDirective.class,
                        org.zexnocs.teanekoagent.expression.PlainReplyProtocol.class, org.zexnocs.teanekoagent.expression.JsonReplyProtocol.class,
                        org.zexnocs.teanekoagent.expression.TextReplyHandler.class, org.zexnocs.teanekoagent.expression.ImageReplyHandler.class);
                beans.refresh(); beans.getBeanFactory().registerSingleton("receipts", delivery);
                runtime = new org.zexnocs.teanekoagent.expression.ExpressionRuntime(new org.zexnocs.teanekoagent.expression.ReplyExtensionRegistry(
                        new org.zexnocs.teanekocore.utils.scanner.BeanScanner(beans, logger), null), org.zexnocs.teanekoagent.expression.ExpressionSettings::defaults,
                        scope -> registered.get() && config.isEnabled() && config.isExpressionEnabled(), db.mapper);
                replies = new org.zexnocs.teanekoagent.expression.ReplyCoordinator(workers, timers, Clock.systemUTC(), runtime::settings);
                sending = new org.zexnocs.teanekoagent.expression.ExpressionDelivery(runtime, replies, beans.getBeanProvider(org.zexnocs.teanekoagent.history.AgentDeliveryService.class));
            }
            /** 先结束自有队列，再释放局部容器，不关闭原线程设施。 */
            @Override public void close() { replies.close(); beans.close(); }
        }

        final AtomicReference<LLMAssistantMessage> mediaAnswer = new AtomicReference<>();
        final AtomicReference<Runnable> onModel = new AtomicReference<>(() -> { });
        final AgentScopeConfigData config = new AgentScopeConfigData();
        final AtomicBoolean registered = new AtomicBoolean(true);
        final IConfigDataService configs = mock(IConfigDataService.class);
        final Handler handler = new Handler();
        final AtomicReference<String> command = new AtomicReference<>("/agent-test");
        final List<ILLMPrompt> prompts = new CopyOnWriteArrayList<>();
        final TeaNekoCommandConverter converter = new TeaNekoCommandConverter(mock(TeaNekoScopeService.class));
        final EasyDataCommandAttemptStore attempts = new EasyDataCommandAttemptStore(db.data, db.mapper);
        final MemoryNamespaceRegistry namespaces = new MemoryNamespaceRegistry(db.store, () -> 1, Clock.systemUTC());
        final EasyDataMemoryNamespaceBindingStore bindingStore = new EasyDataMemoryNamespaceBindingStore(db.store);
        final MemoryNamespaceBindings bindings = new MemoryNamespaceBindings(bindingStore, () -> 1, Clock.systemUTC());
        final StoreAgentMemoryReferences references;
        final CommandDispatcher dispatcher;
        final CommandScanner scanner;
        final TeaNekoHelpSubCommandHandler help;
        final NativeCommandBridge bridge;
        final AgentCommandToolSource tools;
        final AgentService turns;
        final AgentConversationService mailbox;
        final AgentCommandGateway gateway;
        final AgentNamespaceConfigFields configFields;
        final AgentMemoryManagementCommands management;
        final EasyDataPersonalityStore personalityStore = new EasyDataPersonalityStore(db.data, db.mapper);
        final AgentPersonalityManagementCommands personalityManagement;
        final IAgentPersonalitySource personalities;
        final ConversationHistoryService history;
        final ISkillCatalog skillCatalog = mock(ISkillCatalog.class);
        final AgentSkillCommands skillCommands;
        /**
         * 复用原任务服务替身及事务容器，装配真实配置扩展和 DEBUG 记忆管理入口。
         * @throws Exception 当查询端口的测试声明无法访问或配置未找到时
         */
        Harness() throws Exception { this(false); }
        /**
         * 保留既有命令夹具，只有人格管理流程启用真实人格服务。
         * @param realPersonality 是否使用真实人格、配置及存储
         * @throws Exception 当原命令装配或配置声明无法读取时
         */
        Harness(boolean realPersonality) throws Exception {
            this(realPersonality, false);
        }
        /**
         * 复用真实历史与能力边界，仅图文采集用例启用视觉协议及历史记录。
         * @param realPersonality 是否使用真实人格服务
         * @param platformFeatures 是否启用历史及视觉协议
         * @throws Exception 当原生夹具装配失败时
         */
        @SuppressWarnings({"rawtypes", "unchecked"})
        Harness(boolean realPersonality, boolean platformFeatures) throws Exception { this(realPersonality, platformFeatures, false); }
        /**
         * 2026/11/03：可选择完整表达编译与实际回执交付，其他用例不重复装配。
         * @param realPersonality 是否启用真实人格
         * @param platformFeatures 是否启用原历史及视觉能力
         * @param expressions 是否启用拟人交付
         * @throws Exception 当原夹具装配失败时
         */
        @SuppressWarnings({"rawtypes", "unchecked"})
        Harness(boolean realPersonality, boolean platformFeatures, boolean expressions) throws Exception {
            config.setCommandToolsEnabled(true);
            IAgentScopePolicyPort scopes = ignored -> registered.get() ? new AgentScopePolicy(true, config.isEnabled(), AgentScopePolicy.Mode.ACTIVE) : AgentScopePolicy.unregistered();
            references = new StoreAgentMemoryReferences(scopes, bindingStore, namespaces, workers);
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(1000).build()),
                    platformFeatures ? new ModelCapabilities(Set.of(LLMMessageRole.USER, LLMMessageRole.ASSISTANT), Set.of(LLMImageDetail.AUTO))
                            : ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, scopes, definition -> {
                var options = runtime.modelOptions().toOptions();
                definition.modelOptions().toOptions().findMaxTokens().ifPresent(value -> options.setMaxTokens(Math.min(options.getMaxTokens(), value)));
                return new AgentReadiness(AgentReadiness.Status.READY, Optional.of(new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(options), runtime.capabilities(), runtime.interaction())));
            }, logger, automaticAccess);
            var files = mock(IFileConfigService.class); when(files.get(AgentPersonalityFileConfig.class)).thenReturn(new AgentPersonalityFileConfig());
            var configuration = new ConfiguredPersonalities(configs, new AgentScopeConfigManager(), files);
            if (realPersonality) { personalities = new PersonalityService(configuration, personalityStore, workers, Clock.systemUTC(), db.mapper); }
            else {
                personalities = mock(IAgentPersonalitySource.class);
                when(personalities.resolve(any(), any())).thenReturn(CompletableFuture.completedStage(new AgentPersonalitySnapshot("人格", "teaneko", 1, "补正", 0, "基础人格", "", null)));
                when(personalities.verify(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
            }
            history = new ConversationHistoryService(new EasyDataHistoryStore(db.data, db.mapper), references, personalities,
                    scope -> new ConversationHistoryService.Policy(registered.get() && config.isEnabled(), config.isHistoryCaptureEnabled(), config.isHistoryReadEnabled()),
                    workers, Clock.systemUTC(), () -> java.time.Duration.ofDays(7));
            var tasks = mock(ITaskService.class, CALLS_REAL_METHODS);
            doAnswer(call -> {
                ITaskConfig<?> task = call.getArgument(1); var result = new CompletableFuture<ITaskResult<?>>();
                try { result.complete(task.getCallable().call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
                return new TaskFuture(logger, task.getName(), result);
            }).when(tasks).subscribeWithFuture(any(UUID.class), any(ITaskConfig.class), any(Class.class));
            var beans = mock(IBeanScanner.class); var entries = new HashMap<String, org.zexnocs.teanekocore.framework.pair.Pair<Command, Object>>();
            doReturn(entries).when(beans).getBeansWithAnnotation(Command.class); when(beans.getBeanClass(any())).thenAnswer(call -> call.getArgument(0).getClass());
            scanner = new CommandScanner(logger, beans); var eventScanner = mock(EventHandlerScanner.class);
            doReturn(List.of()).when(eventScanner).getEventHandlerList(any());
            var scopeManager = mock(ICommandScopeManager.class);
            when(scopeManager.inScope(any(Command.class), any(DefaultCommand.class), any())).thenReturn(true);
            when(scopeManager.inScope(any(Command.class), any(SubCommand.class), any())).thenReturn(true);
            help = new TeaNekoHelpSubCommandHandler(logger, mock(CommandDescriptionScanner.class), new CommandPermissionManager(), scopeManager);
            dispatcher = new CommandDispatcher(logger, new CommandPermissionManager(), scopeManager, new CommandExecutor(tasks), new CommandArgumentProcessor(logger), scanner, new EventService(logger, eventScanner, tasks));
            when(configs.getConfigData(any(), eq(AgentScopeConfigData.class), anyString())).thenAnswer(call -> registered.get() ? Optional.of(config) : Optional.empty());
            when(configs.registerConfigAsync(any(ConfigManager.class), anyString())).thenAnswer(call -> {
                registered.set(true); config.setEnabled(true); return CompletableFuture.completedStage(null);
            });
            when(configs.unregisterConfigAsync(any(ConfigManager.class), anyString())).thenAnswer(call -> CompletableFuture.completedStage(registered.getAndSet(false)));
            bridge = new NativeCommandBridge(converter, dispatcher, new TeaNekoCommandErrorHandler(logger), help, attempts, workers,
                    gate, registry, references, personalities, configs, new AgentScopeConfigManager());
            tools = new AgentCommandToolSource(bridge, configs, new AgentScopeConfigManager(), db.mapper);
            var models = mock(ILLMModelService.class);
            when(models.resolveOptions(any())).thenAnswer(call -> call.<ILLMPrompt>getArgument(0).getOptions());
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                ILLMPrompt prompt = call.getArgument(0); prompts.add(prompt);
                onModel.get().run();
                var response = mediaAnswer.get() != null ? mediaAnswer.get()
                        : automaticAnswer.get() != null ? LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(automaticAnswer.get()).build()).build()
                        : prompt.getMessages().getLast().getRole() == LLMMessageRole.TOOL || prompt.getOptions().getTools().isEmpty()
                        ? LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("已收到命令处理回执").build()).build()
                        : LLMAssistantMessage.builder().contents(List.of()).toolCalls(List.of(LLMToolCall.builder().id("原调用-" + prompts.size())
                                .name(AgentCommandToolSource.NAME).arguments(db.mapper.writeValueAsString(Map.of("command", command.get()))).build())).build();
                return new TaskFuture<>(logger, "离线命令模型", CompletableFuture.completedFuture(LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build())
                        .choices(List.of(LLMChoice.builder().index(0).finishReason(response.getToolCalls().isEmpty() ? "stop" : "tool_calls").message(response).build())).build()));
            });
            expression = expressions ? new ExpressionHarness() : null;
            turns = new AgentService(gate, new LLMInteractionService(models, LLMInteractionSettings::defaults, workers, timers),
                    (definition, request, actual) -> tools.select(definition, request), db.mapper, workers, timers, Clock.systemUTC(), logger, references, expression == null ? IAgentPromptSource.empty() : new org.zexnocs.teanekoagent.expression.ExpressionFeature(expression.runtime).prompts(), personalities,
                    platformFeatures ? history : null);
            if (expression != null) { turns.installExpressions(expression.runtime); }
            mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults, workers, timers, Clock.systemUTC(), logger, AgentConversationSettings::defaults,
                    (definition, request) -> AgentConversationOptions.defaults(), db.mapper, references, personalities);
            gateway = new AgentCommandGateway(new AgentFactory(registry), registry, mailbox, mailbox, scopes, bridge, references, bindings, namespaces, () -> 1, timers, attempts);
            // 2026/11/03：默认发送等待真实回执提交。
            gateway.setDelivery(delivery);
            if (expression != null) { gateway.setExpressions(expression.sending, expression.replies); }
            configFields = new AgentNamespaceConfigFields(configs, new AgentScopeConfigManager(), bindings, new AgentNamespaceCommands(scopes, references, bindings, mailbox, timers));
            management = new AgentMemoryManagementCommands(new EasyDataMemoryManagementStore(db.store, db.facts, db.data,
                    () -> 1, db.mapper, new org.springframework.mock.env.MockEnvironment()), db.store, scopes, mailbox, timers, db.mapper);
            personalityManagement = new AgentPersonalityManagementCommands(personalityStore, personalityStore, configuration, scopes, mailbox, timers, db.mapper);
            var snapshot = new SkillCatalogSnapshot(List.of(new SkillDefinition("clear-explanation", "清晰解释", 1, true, List.of(), "分步解释并核对事实。")));
            when(skillCatalog.snapshot()).thenReturn(snapshot);
            when(skillCatalog.reload(any(Runnable.class))).thenAnswer(call -> { call.<Runnable>getArgument(0).run(); return snapshot; });
            var skillRuntime = new AgentSkills(skillCatalog, new ConfiguredSkillPolicy(configs, new AgentScopeConfigManager(), new org.springframework.mock.env.MockEnvironment()), db.mapper, workers);
            skillCommands = new AgentSkillCommands(skillRuntime, skillCatalog, scopes, workers, timers);
            var configScanner = mock(ConfigManagerScanner.class); when(configScanner.getConfigManager("agent")).thenReturn(AgentScopeConfigManager.class.getAnnotation(ConfigManager.class));
            var queries = mock(IConfigDataQueryService.class); when(queries.queryOneConfigManagerDetailInObject(any(), anyString())).thenReturn("Agent 配置");
            for (var bean : List.of(handler, new AgentCommand(gateway, management, personalityManagement, skillCommands), new TeaNekoConfigCommand(logger, configScanner, configs, queries, List.of(configFields)))) {
                entries.put(bean.getClass().getName(), HashPair.of(bean.getClass().getAnnotation(Command.class), bean));
            }
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
        /** 2026/11/03：先释放表达队列，再关闭原拥有者；不阻塞未开始的等待。 */
        @Override public void close() { handler.business.complete("关闭验证"); gateway.close(); if (expression != null) { expression.close(); } mailbox.close(); turns.close(); registry.close(); timers.shutdownNow(); workers.close(); db.close(); }
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
    /**
     * 验证原事务唯一执行权、服务重建后的未知在途、参数冲突和持久化终态，不重复副作用。
     * @throws Exception 当命令夹具的查询声明无法装配时
     */
    @Test void durableAttemptsDoNotReexecuteAfterRebuildOrConflict() throws Exception {
        try (var h = new Harness()) {
            var actor = NativeCommandBridge.actor(h.source("甲", CommandPermission.OWNER, "来源"));
            var reference = h.references.resolve(actor.scope()).toCompletableFuture().join();
            var attempt = new ICommandAttemptStore.Attempt(UUID.randomUUID(), "调用", actor, reference, StableDigest.sha256("/agent-test"));
            var a = h.attempts.claim(attempt); var b = h.attempts.claim(attempt);
            assertNotEquals(a.toCompletableFuture().join().acquired(), b.toCompletableFuture().join().acquired());
            var rebuilt = new EasyDataCommandAttemptStore(h.db.data, h.db.mapper);
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

    /**
     * 验证 /cfg 与 /agent 共用权威绑定，成员不能越权，注销/重新登记不丢失原空间。
     * @throws Exception 当真实命令或异步绑定未按时结束时
     */
    @Test void configNamespaceUsesSameAuthorityAndSurvivesRegistration() throws Exception {
        try (var permissions = mockStatic(CommandEasyData.class); var h = new Harness()) {
            var empty = mock(CommandEasyData.class); when(empty.get(anyString())).thenReturn(mock(IEasyDataDto.class));
            permissions.when(() -> CommandEasyData.of(anyString())).thenReturn(empty);
            var owner = h.source("甲", CommandPermission.OWNER, "/agent reset");
            h.gateway.changeNamespace(owner, null, null).toCompletableFuture().join(); var scope = NativeCommandBridge.actor(owner).scope();
            var target = h.bindings.readReference(scope).toCompletableFuture().join().orElseThrow().namespaceId();
            var member = h.source("成员", CommandPermission.MEMBER, "/cfg set agent memoryNamespace default");
            assertEquals(CommandResult.Status.PERMISSION_DENIED, h.dispatcher.dispatch(h.converter.__parse(member), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            var change = h.source("甲", CommandPermission.OWNER, "/cfg set agent memoryNamespace default");
            assertEquals(CommandResult.Status.COMPLETED, h.dispatcher.dispatch(h.converter.__parse(change), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            assertEquals(MemoryNamespaceId.SHARED, h.bindings.readReference(scope).toCompletableFuture().join().orElseThrow().namespaceId());
            h.gateway.changeNamespace(h.source("甲", CommandPermission.OWNER, "绑定原空间"), target.value(), null).toCompletableFuture().join();
            var query = h.source("甲", CommandPermission.OWNER, "/cfg agent");
            h.dispatcher.dispatch(h.converter.__parse(query), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join();
            assertTrue(h.configFields.read(h.converter.__parse(query)).toCompletableFuture().join().contains(target.value()));
            var unreg = h.source("甲", CommandPermission.OWNER, "/cfg unreg agent");
            h.dispatcher.dispatch(h.converter.__parse(unreg), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join();
            assertTrue(h.configFields.read(h.converter.__parse(query)).toCompletableFuture().join().isEmpty());
            assertEquals(target, h.bindings.readReference(scope).toCompletableFuture().join().orElseThrow().namespaceId());
            var reg = h.source("甲", CommandPermission.OWNER, "/cfg reg agent");
            h.dispatcher.dispatch(h.converter.__parse(reg), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join();
            assertTrue(h.configFields.read(h.converter.__parse(query)).toCompletableFuture().join().contains(target.value()));
            verify(h.configs, never()).setRuleConfigFieldAsync(any(), anyString(), eq("memoryNamespace"), anyString());
        }
    }

    /**
     * 验证作用域显式 Skill 选择和原生 DEBUG 重载权限，目录元数据不能提升角色。
     * @throws Exception 当原命令或反馈阶段未按时结束时
     */
    @Test void skillCommandsRespectScopeAndDebugReloadPermission() throws Exception {
        try (var h = new Harness()) {
            h.config.setSkills("clear-explanation"); h.config.setSkillsEnabled(true);
            var member = h.source("成员", CommandPermission.MEMBER, "/agent skills");
            assertEquals(CommandResult.Status.COMPLETED, h.dispatcher.dispatch(h.converter.__parse(member), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            assertTrue(h.skillCommands.execute(member, false, null).toCompletableFuture().join().contains("clear-explanation"));
            var owner = h.source("群主", CommandPermission.OWNER, "/agent reloadskills");
            assertEquals(CommandResult.Status.PERMISSION_DENIED, h.dispatcher.dispatch(h.converter.__parse(owner), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            assertThrows(CompletionException.class, () -> h.skillCommands.execute(owner, true, null).toCompletableFuture().join());
            verify(h.skillCatalog, never()).reload(any(Runnable.class));
            var debug = h.source("调试者", CommandPermission.DEBUG, "/agent reloadskills");
            assertEquals(CommandResult.Status.COMPLETED, h.dispatcher.dispatch(h.converter.__parse(debug), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            verify(h.skillCatalog).reload(any(Runnable.class));
            h.config.setEnabled(false);
            assertThrows(CompletionException.class, () -> h.skillCommands.execute(debug, true, null).toCompletableFuture().join());
        }
    }

    /**
     * 经真实命令扫描/绑定验证人格管理和权限，正文与字段实际进入专用存储。
     * @throws Exception 当原命令或管理事务未按时完成时
     */
    @Test void debugPersonalityCommandsUseRealPermissionsAndStorage() throws Exception {
        try (var h = new Harness(true)) {
            var owner = h.source("群主", CommandPermission.OWNER, "/agent personality");
            assertEquals(CommandResult.Status.PERMISSION_DENIED, h.dispatcher.dispatch(h.converter.__parse(owner), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            assertThrows(CompletionException.class, () -> h.personalityManagement.page(owner, "", 0, false, false, null).toCompletableFuture().join());
            var query = h.source("调试者", CommandPermission.DEBUG, "/agent personality");
            assertEquals(CommandResult.Status.COMPLETED, h.dispatcher.dispatch(h.converter.__parse(query), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            var scope = NativeCommandBridge.actor(query).scope(); var binding = h.personalityStore.binding(scope).toCompletableFuture().join().orElseThrow();
            var prefix = binding.correctionKey() + " " + binding.revision() + " ";
            var add = h.source("调试者", CommandPermission.DEBUG, "/agent addcorrection " + prefix + "0 speaking_detail 自然交流");
            h.dispatcher.dispatch(h.converter.__parse(add), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join();
            var record = h.personalityStore.state(binding.correctionKey()).toCompletableFuture().join().orElseThrow().corrections().getFirst();
            assertEquals("speaking detail", record.field()); assertEquals("自然交流", record.text());
            assertEquals(NativeCommandBridge.actor(add), record.source());
            var readOnly = h.source("调试者", CommandPermission.DEBUG, "/agent addcorrection " + prefix + "1 identity.name 不能覆盖身份");
            h.dispatcher.dispatch(h.converter.__parse(readOnly), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join();
            assertEquals(1, h.personalityStore.state(binding.correctionKey()).toCompletableFuture().join().orElseThrow().revision());
            for (var text : List.of("/agent editcorrection " + prefix + "1 " + record.requestId() + " 保留来源的新表达",
                    "/agent lockcorrection " + prefix + "2 " + record.requestId() + " true",
                    "/agent backuppersonality " + prefix + "3", "/agent clonecorrections " + prefix + "3",
                    "/agent exportpersonality " + binding.correctionKey(), "/agent personalitybackups " + binding.correctionKey(),
                    "/agent revokecorrection " + prefix + "3 " + record.requestId(),
                    "/agent setcorrectionkey 管理新空间 " + binding.revision() + " -1")) {
                var source = h.source("调试者", CommandPermission.DEBUG, text);
                assertEquals(CommandResult.Status.COMPLETED, h.dispatcher.dispatch(h.converter.__parse(source), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().get(10, TimeUnit.SECONDS).status(), text);
            }
            assertEquals("管理新空间", h.personalityStore.binding(scope).toCompletableFuture().join().orElseThrow().correctionKey());
            assertTrue(h.personalityStore.state(binding.correctionKey()).toCompletableFuture().join().orElseThrow().corrections().isEmpty());
            assertFalse(h.personalityStore.backups(binding.correctionKey()).toCompletableFuture().join().isEmpty());
            h.config.setEnabled(false);
            assertThrows(CompletionException.class, () -> h.personalityManagement.page(query, "", 0, false, true, null).toCompletableFuture().join());
        }
    }

    /**
     * 验证真实 DEBUG 原权限、编辑/导出/克隆/清空入口及同一共享空间的跨群会话失效。
     * @throws Exception 当原命令或后台事务未按时完成时
     */
    @Test void debugManagementClearsAllSharedScopesWithoutChangingClone() throws Exception {
        try (var h = new Harness()) {
            h.config.setCommandToolsEnabled(false);
            var first = h.source("甲", CommandPermission.MEMBER, "/agent chat 首群");
            var second = h.client.handleGroupMessage("另一群", "乙", "用户", CommandPermission.MEMBER, "/agent chat 另群").getData();
            h.gateway.chat(first, "首群", null).toCompletableFuture().join(); h.gateway.chat(second, "另群", null).toCompletableFuture().join();
            var handle = h.registry.find(h.registry.snapshots().getFirst().id()).orElseThrow();
            var firstScope = NativeCommandBridge.actor(first).scope(); var secondScope = NativeCommandBridge.actor(second).scope();
            var firstStamp = h.mailbox.snapshot(handle, firstScope).orElseThrow().require(AgentConversationState.COMPONENT).stamp();
            var secondStamp = h.mailbox.snapshot(handle, secondScope).orElseThrow().require(AgentConversationState.COMPONENT).stamp();
            var owner = h.source("群主", CommandPermission.OWNER, "/agent clear default 0 0");
            assertEquals(CommandResult.Status.PERMISSION_DENIED, h.dispatcher.dispatch(h.converter.__parse(owner), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().join().status());
            assertThrows(CompletionException.class, () -> h.management.change(owner, "default", 0, 0, false, null).toCompletableFuture().join());
            var actor = NativeCommandBridge.actor(first); var id = UUID.randomUUID();
            h.db.facts.append(new MemoryFactAppend(id, new MemoryNamespaceReference(firstScope, MemoryNamespaceId.SHARED, 0, 0), actor.userId().orElseThrow(),
                    new MemoryFactValue(MemoryFactValue.Kind.FACT, "原始事实".repeat(800), List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null),
                    new MemoryFactSource(UUID.randomUUID(), "新增", actor)), Clock.systemUTC().instant()).toCompletableFuture().join();
            for (var text : List.of("/agent memory default", "/agent exportmemory default 0", "/agent lockmemory default 0 " + id + " 0 true",
                    "/agent lockmemory default 0 " + id + " 1 非法值", "/agent editmemory default 0 " + id + " 1 修正事实",
                    "/agent clonememory default 0 0", "/agent clear default 0 0")) {
                var source = h.source("调试者", CommandPermission.DEBUG, text);
                assertEquals(CommandResult.Status.COMPLETED, h.dispatcher.dispatch(h.converter.__parse(source), new TeaNekoCommandErrorHandler(h.logger), h.help).toCompletableFuture().get(10, TimeUnit.SECONDS).status());
            }
            var debug = h.source("调试者", CommandPermission.DEBUG, "检查");
            var clone = h.db.store.listCreated(debug.getUserData().getUuid(), 0, 10).toCompletableFuture().join().getFirst();
            var copied = h.db.facts.list(clone.id(), 0, null, 10).toCompletableFuture().join().records().getFirst();
            assertEquals("修正事实", copied.value().text()); assertTrue(copied.locked());
            assertEquals(1, h.db.store.find(MemoryNamespaceId.SHARED).toCompletableFuture().join().orElseThrow().generation());
            assertNotEquals(firstStamp, h.mailbox.snapshot(handle, firstScope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
            assertNotEquals(secondStamp, h.mailbox.snapshot(handle, secondScope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
        }
    }

    /**
     * 2026/11/02：私聊无需提及即可回复，元数据仅送入模型，关闭开关仍拒绝。
     * @throws Exception 当原生夹具装配失败时
     */
    @Test void privateMessagesReplyWithoutMentionsAndKeepMetadataOutOfReplies() throws Exception {
        try (var h = new Harness(); var automatic = automatic(h)) {
            h.automaticAnswer.set("你好"); var receiver = receiver(h, automatic);
            var source = h.client.handlePrivateMessage("alice", "私聊昵称", "你好").getData();
            assertTrue(receiver.receive(source).toCompletableFuture().get(10, TimeUnit.SECONDS));
            var prompt = h.db.mapper.writeValueAsString(h.prompts.getFirst().getMessages());
            assertTrue(prompt.contains(source.getUserData().getUuid().toString())); assertTrue(prompt.contains(source.getScopeId()));
            assertTrue(prompt.contains("私聊昵称")); assertFalse(h.db.mapper.writeValueAsString(h.client.getSentMessages()).contains("senderUuid"));
            h.config.setEnabled(false);
            assertFalse(receiver.receive(h.client.handlePrivateMessage("alice", "私聊昵称", "还在吗").getData()).toCompletableFuture().join());
        }
    }

    /**
     * 原生提及走真实 Agent 和 FakeClient，多段保持全文且重建后不重复执行原消息。
     * @throws Exception 当夹具装配或异步交付失败时
     */
    @Test void automaticInboundDeliversCompletePlanAndDurablyDeduplicates() throws Exception {
        try (var h = new Harness(); var automatic = automatic(h)) {
            var answer = "文".repeat(1999) + "😀" + "尾".repeat(2000); h.automaticAnswer.set(answer);
            var receiver = receiver(h, automatic); var source = incoming(h, "alice", "你好", true);
            assertTrue(receiver.receive(source).toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertEquals(3, h.client.getSentMessages().size());
            var delivered = h.client.getSentMessages().stream().flatMap(message -> message.getMessages().stream())
                    .map(item -> item.getContentPart()).filter(org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart.class::isInstance)
                    .map(org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart.class::cast).map(part -> part.getText()).collect(java.util.stream.Collectors.joining());
            assertEquals(answer, delivered); assertEquals(1, h.prompts.size());
            assertFalse(receiver.receive(source).toCompletableFuture().join());
            try (var rebuilt = automatic(h)) { assertFalse(receiver(h, rebuilt).receive(source).toCompletableFuture().join()); }
            assertEquals(1, h.prompts.size()); assertEquals(3, h.client.getSentMessages().size());
        }
    }

    /**
     * 当前原生作者与引用来源决定触发，旁观、机器人自己和原有指令不进入模型。
     * @throws Exception 当原生夹具装配失败时
     */
    @Test void automaticSignalsRespectQuoteScopeAndContinuationOwnership() throws Exception {
        try (var h = new Harness(); var automatic = automatic(h)) {
            h.automaticAnswer.set("收到"); var receiver = receiver(h, automatic);
            assertFalse(receiver.receive(incoming(h, "alice", "旁观", false)).toCompletableFuture().join());
            var foreign = h.client.handleGroupMessage("其他群", "fake-bot", "机器人", CommandPermission.MEMBER, "引用正文").getData();
            var quote = incoming(h, "alice", "当前原文", false);
            quote.setMessages(new DefaultTeaNekoContentListBuilder().addReply(foreign.getMessageId()).addText("当前原文").build());
            assertFalse(receiver.receive(quote).toCompletableFuture().join());
            var bot = h.source("fake-bot", CommandPermission.MEMBER, "引用正文");
            quote.setMessages(new DefaultTeaNekoContentListBuilder().addReply(bot.getMessageId()).addText("当前原文").build());
            assertTrue(receiver.receive(quote).toCompletableFuture().join());
            h.config.setTriggerNames("茶猫");
            assertFalse(receiver.receive(incoming(h, "bob", "茶猫，您好", false)).toCompletableFuture().join());
            h.config.setMode(AgentScopePolicy.Mode.PASSIVE_CONTEXTUAL.name());
            assertTrue(receiver.receive(incoming(h, "alice", "继续聊", false)).toCompletableFuture().join());
            assertFalse(receiver.receive(incoming(h, "bob", "旁观者续聊", false)).toCompletableFuture().join());
            assertTrue(receiver.receive(incoming(h, "bob", "茶猫，您好", false)).toCompletableFuture().join());
            assertFalse(receiver.receive(incoming(h, "fake-bot", "机器人回显", true)).toCompletableFuture().join());
            assertFalse(receiver.receive(incoming(h, "alice", "/agent status", true)).toCompletableFuture().join());
            assertEquals(3, h.prompts.size());
        }
    }

    /**
     * 模型返回前关停使原管线、交付及旧票据全部失效，恢复后新消息仍可正常完成。
     * @throws Exception 当原生夹具装配失败时
     */
    @Test void automaticPolicyChangeBlocksLateAnswerAndReleasesOwnership() throws Exception {
        try (var h = new Harness(); var automatic = automatic(h)) {
            h.automaticAnswer.set("不可交付的旧答案"); var receiver = receiver(h, automatic);
            h.registered.set(false);
            assertFalse(receiver.receive(incoming(h, "alice", "未登记", true)).toCompletableFuture().join()); assertTrue(h.prompts.isEmpty());
            h.registered.set(true); h.onModel.set(() -> h.config.setEnabled(false));
            assertFalse(receiver.receive(incoming(h, "alice", "执行中关闭", true)).toCompletableFuture().join());
            assertTrue(h.client.getSentMessages().isEmpty());
            h.config.setEnabled(true); h.onModel.set(() -> { }); h.automaticAnswer.set("新消息已完成");
            assertTrue(receiver.receive(incoming(h, "alice", "恢复", true)).toCompletableFuture().join());
            assertEquals(1, h.client.getSentMessages().size());
            h.automaticAnswer.set(null); h.handler.business.complete("原生业务完成");
            var owner = (TeaNekoMessageData) h.source("owner", CommandPermission.OWNER, "请执行原生命令");
            owner.setMessages(new DefaultTeaNekoContentListBuilder().addAt("fake-bot").addText("请执行原生命令").build());
            assertTrue(receiver.receive(owner).toCompletableFuture().join());
            assertEquals(1, h.handler.calls.get()); assertSame(owner, h.handler.received.getRawData());
            assertEquals(CommandPermission.OWNER, h.handler.received.getPermission());
        }
    }

    /**
     * 使用原事务、实际 Agent 管线及 FakeClient 的共享夹具。
     * @param h 原有集成夹具
     * @return 独立拥有者的自动回复应用服务
     */
    private AgentAutomaticGateway automatic(Harness h) {
        // 2026/11/03：自动消息走默认持久回执路径。
        var gateway = new AgentAutomaticGateway(new AgentFactory(h.registry), h.registry, h.mailbox, h.references, h.attempts, h.automaticAccess, h.triggers, h.bridge);
        gateway.setDelivery(h.delivery);
        // 2026/11/03：自动入口与命令使用同一个实际目标队列和回执服务。
        if (h.expression != null) { gateway.setExpressions(h.expression.sending, h.expression.replies); }
        return gateway;
    }

    /**
     * 接入真实命令目录与原生转换，不伪造命令未命中状态。
     * @param h 原夹具
     * @param automatic 自动回复拥有者
     * @return 可等待的实际接收器
     */
    private NativeAgentReceiver receiver(Harness h, AgentAutomaticGateway automatic) {
        return new NativeAgentReceiver(h.configs, new AgentScopeConfigManager(), h.triggers, automatic, h.converter, h.scanner, h.workers, h.logger);
    }

    /**
     * 保留实际消息 ID、scope、用户与客户端，只增加原生提及内容。
     * @param h 原夹具
     * @param user 真实用户平台来源
     * @param text 当前原文
     * @param mention 是否包含实际机器人提及
     * @return 实际 FakeClient 入站数据
     */
    private TeaNekoMessageData incoming(Harness h, String user, String text, boolean mention) {
        var source = (TeaNekoMessageData) h.source(user, CommandPermission.MEMBER, text);
        var contents = new DefaultTeaNekoContentListBuilder(); if (mention) { contents.addAt("fake-bot"); }
        source.setMessages(contents.addText(text).build()); return source;
    }

    /**
     * 旁观采集不调用模型，重投不延长保留期，原生时间与触发轮次采用相同依据。
     * @throws Exception 当原生夹具装配失败时
     */
    @Test void passiveCaptureUsesNativeTimeAndIndependentSwitches() throws Exception {
        try (var h = new Harness(false, true); var automatic = automatic(h)) {
            var capture = capture(h); var receiver = receiver(h, automatic, capture);
            var source = incoming(h, "alice", "旁观文本", false);
            source.setTime(Clock.systemUTC().instant().minusSeconds(2));
            assertFalse(receiver.receive(source).toCompletableFuture().join()); assertTrue(h.prompts.isEmpty());
            assertFalse(receiver.receive(source).toCompletableFuture().join());
            var page = history(h, source); assertEquals(1, page.messages().size());
            assertFalse(page.messages().getFirst().estimatedTime());
            assertEquals(source.getTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS), page.messages().getFirst().occurredAt());
            h.config.setHistoryCaptureEnabled(false);
            assertFalse(receiver.receive(incoming(h, "alice", "采集关闭", false)).toCompletableFuture().join());
            h.config.setHistoryCaptureEnabled(true); h.registered.set(false);
            assertFalse(receiver.receive(incoming(h, "alice", "未登记", false)).toCompletableFuture().join());
            h.registered.set(true); assertEquals(1, history(h, source).messages().size());
        }
    }

    /**
     * 原生图文顺序进入实际模型，显式图片输出走平台发送器，采集与轮次只保留一条用户原文。
     * @throws Exception 当原生夹具装配失败时
     */
    @Test void orderedMediaInputOutputAndHistoryShareOneNativeSource() throws Exception {
        try (var h = new Harness(false, true); var automatic = automatic(h)) {
            h.mediaAnswer.set(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("说明前")
                    .addImage("data:image/png;base64,AQID").addText("说明后").build()).build());
            var source = incoming(h, "alice", "看图前看图后", true);
            source.setMessages(new DefaultTeaNekoContentListBuilder().addAt("fake-bot").addText("看图前")
                    .addImage("https://images.example/image.png").addText("看图后").build());
            var receiver = receiver(h, automatic, capture(h));
            assertTrue(receiver.receive(source).toCompletableFuture().join());
            // 2026/11/02：首个文本块为独立平台上下文，后面的原始图文顺序不变。
            assertEquals(List.of("text", "image", "text"), h.prompts.getFirst().getMessages().getLast().getContents().stream().skip(1).map(item -> item.getType()).toList());
            assertEquals(3, h.client.getSentMessages().size());
            var image = h.client.getSentMessages().get(1).getMessages().stream().map(item -> item.getContentPart())
                    .filter(org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart.class::isInstance)
                    .map(org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart.class::cast).findFirst().orElseThrow();
            assertEquals("base64://AQID", image.getUrl());
            assertEquals(1, history(h, source).messages().stream().filter(item -> item.role() == HistoryMessage.Role.USER).count());
            h.mediaAnswer.set(null); h.automaticAnswer.set("图片已收到");
            var imageOnly = incoming(h, "bob", "", true);
            imageOnly.setMessages(new DefaultTeaNekoContentListBuilder().addAt("fake-bot").addImage("https://images.example/only.png").build());
            assertTrue(receiver.receive(imageOnly).toCompletableFuture().join());
            var invalid = incoming(h, "bob", "读取本地图片", true);
            invalid.setMessages(new DefaultTeaNekoContentListBuilder().addAt("fake-bot").addText("读取本地图片").addImage("file:///private.png").build());
            assertThrows(CompletionException.class, () -> receiver.receive(invalid).toCompletableFuture().join());
            assertEquals(2, h.prompts.size());
        }
    }

    /**
     * 已采集旧消息换代后不重放，自动邮箱不把原固定引用刷新为新代次执行。
     * @throws Exception 当原生夹具装配失败时
     */
    @Test void capturedSourceCannotReenterNewGeneration() throws Exception {
        try (var h = new Harness(false, true)) {
            var source = incoming(h, "alice", "旧原文", false); var input = NativeMessageInput.from(source); var collector = capture(h);
            var original = collector.capture(source, input).toCompletableFuture().join(); assertTrue(original.accepted());
            var context = Objects.requireNonNull(original.context()); var actor = NativeCommandBridge.actor(source);
            var debug = h.source("debug", CommandPermission.DEBUG, "/agent clear default 0 0");
            h.management.change(debug, "default", 0, 0, false, null).toCompletableFuture().join();
            assertFalse(collector.capture(source, input).toCompletableFuture().join().accepted());
            assertTrue(history(h, source).messages().isEmpty());
            var ticket = h.triggers.acquire(new AgentTriggerGate.Signal(actor, AgentTriggerGate.Kind.MESSAGE, true, false, false, false)).orElseThrow();
            var handle = new AgentFactory(h.registry).create(new org.zexnocs.teanekoagent.agent.AgentDefinition("旧采集测试", new LLMModelOptionsSnapshot(null), Set.of(org.zexnocs.teanekoagent.agent.AgentActivity.values())));
            h.automaticAccess.bind(ticket);
            try {
                var request = new org.zexnocs.teanekoagent.agent.AgentRunRequest(UUID.randomUUID(), actor, org.zexnocs.teanekoagent.agent.AgentActivity.AUTOMATIC_REPLY,
                        List.of(input.message()), null, context.reference(), new org.zexnocs.teanekoagent.agent.AgentUserText(actor.sourceId(), input.text(), input.occurredAt()));
                var result = h.mailbox.run(handle, request).toCompletableFuture().join();
                assertNotEquals(org.zexnocs.teanekoagent.agent.AgentRunResult.Status.SUCCESS, result.status()); assertTrue(h.prompts.isEmpty());
            } finally { h.automaticAccess.release(ticket); h.triggers.finish(ticket, false); h.registry.remove(handle.id()); }
        }
    }

    /**
     * 复用真实历史和原有持久化防重，不新增数据库替身。
     * @param h 原生夹具
     * @return 实际平台采集服务
     */
    private PlatformHistoryCapture capture(Harness h) {
        return new PlatformHistoryCapture(h.configs, new AgentScopeConfigManager(), h.references, h.personalities, h.history, h.attempts, h.timers);
    }

    /**
     * 在原自动入站服务组合真实采集器。
     * @param h 原生夹具
     * @param automatic 自动回复拥有者
     * @param capture 原生历史采集
     * @return 实际接收器
     */
    private NativeAgentReceiver receiver(Harness h, AgentAutomaticGateway automatic, PlatformHistoryCapture capture) {
        return new NativeAgentReceiver(h.configs, new AgentScopeConfigManager(), h.triggers, automatic, h.converter, h.scanner, h.workers, h.logger, capture);
    }

    /**
     * 通过真实历史业务边界查询当前有效分区，控制由本方法拥有并关闭。
     * @param h 原夹具
     * @param source 当前原生来源
     * @return 当前历史页
     */
    private HistoryPage history(Harness h, ITeaNekoMessageData source) {
        try (var control = org.zexnocs.teanekocore.actuator.execution.ExecutionControl.create()) {
            var scope = NativeCommandBridge.actor(source).scope();
            var reference = h.references.resolve(scope).toCompletableFuture().join();
            var personality = h.personalities.resolve(scope, control).toCompletableFuture().join();
            return h.history.search(new HistoryContext(reference, personality), new HistoryQuery(Long.MAX_VALUE, "", null, null, 20, 6000), control).toCompletableFuture().join();
        }
    }

    /**
     * 自主接话使用 SYSTEM、独立一次预算及空工具，慢或失败的监控监听不阻断真实发送。
     * 2026/11/03：通过共享表达队列运行，保持原零工具及身份边界。
     * @throws Exception 当原生夹具装配或候选执行失败时
     */
    @Test void proactiveInterjectionUsesSystemAndIsolatesMonitoring() throws Exception {
        try (var h = new Harness(false, true, true)) {
            active(h); var source = incoming(h, "alice", "讨论进行中", false); var targets = proactiveTargets(h, source);
            var events = new DefaultListableBeanFactory(); var bus = mock(IEventService.class);
            var listener = new CompletableFuture<ITaskResult<Void>>();
            when(bus.pushEventWithFuture(any())).thenReturn(new TaskFuture<>(h.logger, "延迟监控", listener)); events.registerSingleton("事件", bus);
            var monitor = new AgentRuntimeMonitor(Runnable::run, events.getBeanProvider(IEventService.class)); h.turns.observe(monitor::accept);
            var turns = spy(h.turns);
            try (var proactive = proactive(h, targets, monitor, turns); var automatic = automatic(h)) {
                var timers = mock(ITimerService.class); var coordinator = new ProactiveCoordinator(h.configs, new AgentScopeConfigManager(), targets, proactive, timers, h.workers);
                var receiver = new NativeAgentReceiver(h.configs, new AgentScopeConfigManager(), h.triggers, automatic, h.converter, h.scanner, h.workers, h.logger, capture(h), coordinator);
                h.automaticAnswer.set("{\"speak\":true,\"text\":\"可以继续讨论这个问题。\"}");
                try (coordinator) {
                    assertTrue(receiver.receive(source).toCompletableFuture().get(10, TimeUnit.SECONDS));
                    assertEquals(1, h.prompts.size()); assertTrue(h.prompts.getFirst().getOptions().getTools().isEmpty());
                    assertTrue(h.prompts.getFirst().getOptions().getMaxTokens() <= 384);
                    var request = org.mockito.ArgumentCaptor.forClass(org.zexnocs.teanekoagent.agent.AgentRunRequest.class);
                    verify(turns).prepare(any(), request.capture(), any());
                    assertEquals(org.zexnocs.teanekoagent.agent.AgentActorContext.Origin.SYSTEM, request.getValue().actor().origin());
                    assertTrue(request.getValue().actor().userId().isEmpty()); assertNull(request.getValue().userText());
                    assertEquals(0, h.handler.calls.get()); assertEquals(1, h.client.getSentMessages().size());
                    assertFalse(listener.isDone()); listener.completeExceptionally(new IllegalStateException("监听故障。"));
                    var report = org.mockito.ArgumentCaptor.forClass(org.zexnocs.teanekoagent.agent.monitor.AgentTurnReportEvent.class);
                    verify(bus).pushEventWithFuture(report.capture());
                    var alerts = new org.zexnocs.teanekoagent.agent.monitor.AgentTokenAlerts(new org.springframework.mock.env.MockEnvironment()
                            .withProperty("tea-neko.agent.monitor.alert-tokens", "1"), bus, h.logger);
                    alerts.onReport(report.getValue()); alerts.onReport(report.getValue());
                    verify(bus).pushEvent(any(org.zexnocs.teanekoagent.agent.monitor.AgentTokenAlertEvent.class));
                    var debug = h.source("debug", CommandPermission.DEBUG, "/agent monitor");
                    var handler = new AgentCommand(h.gateway, h.management, h.personalityManagement, h.skillCommands, null, monitor);
                    handler.monitor(CommandData.<ITeaNekoMessageData>builder().rawData(debug).build()).toCompletableFuture().join();
                    var description = monitor.describe(NativeCommandBridge.actor(source).scope());
                    assertTrue(description.contains("自主发送：1")); assertTrue(description.contains("失败计数：1"));
                    assertFalse(description.contains("讨论进行中"));
                    assertTrue(history(h, source).messages().stream().noneMatch(message -> message.text().contains("\"speak\"")));
                }
            }
        }
    }

    /**
     * 原 Timer 重建只安排未来候选，明确弃言不发送、不保存控制信封，也不因即时重扫重复调用。
     * 2026/11/03：共享目标队列不改变弃言、时间和原持久准入语义。
     * @throws Exception 当原生夹具装配失败时
     */
    @SuppressWarnings("unchecked") @Test void proactiveTimerRebuildsFutureAndCanAbstain() throws Exception {
        try (var h = new Harness(false, true, true)) {
            active(h); h.config.setProactiveIntervalSeconds(60);
            var source = incoming(h, "alice", "用于判断是否需要发言的资料", false); var targets = proactiveTargets(h, source);
            capture(h).capture(source, NativeMessageInput.from(source)).toCompletableFuture().join();
            var monitor = monitor(h); h.turns.observe(monitor::accept);
            try (var proactive = proactive(h, targets, monitor, h.turns)) {
                when(h.configs.getAllConfigKeys(any(Object.class))).thenReturn(List.of(source.getScopeId()));
                var timers = mock(ITimerService.class); ITimerTaskConfig<Void> timer = mock(ITimerTaskConfig.class);
                when(timers.registerByDelay(anyString(), anyString(), any(), any(), eq(Void.class))).thenReturn(timer);
                var now = new AtomicReference<>(Clock.systemUTC().instant()); var clock = mock(Clock.class); when(clock.instant()).thenAnswer(ignored -> now.get());
                h.automaticAnswer.set("{\"speak\":false,\"text\":\"\"}");
                try (var coordinator = new ProactiveCoordinator(h.configs, new AgentScopeConfigManager(), targets, proactive, timers, h.workers, clock)) {
                    coordinator.onReady(); coordinator.onReady(); coordinator.tick().toCompletableFuture().join(); assertTrue(h.prompts.isEmpty());
                    now.set(now.get().plusSeconds(61)); coordinator.tick().toCompletableFuture().join();
                    assertEquals(1, h.prompts.size()); assertTrue(h.client.getSentMessages().isEmpty());
                    coordinator.tick().toCompletableFuture().join(); assertEquals(1, h.prompts.size());
                }
                try (var rebuilt = new ProactiveCoordinator(h.configs, new AgentScopeConfigManager(), targets, proactive, timers, h.workers, clock)) {
                    rebuilt.onReady(); rebuilt.tick().toCompletableFuture().join(); assertEquals(1, h.prompts.size());
                }
                assertEquals(1, history(h, source).messages().size());
                assertTrue(monitor.describe(NativeCommandBridge.actor(source).scope()).contains("放弃：1"));
            }
        }
    }

    /**
     * 当前动作开关变化拒绝晚到发言，候选退避防止重试风暴，格式错误不能被当作正文发送。
     * 2026/11/03：在新队列接入后继续核验撤销及迟到结果。
     * @throws Exception 当原生夹具装配失败时
     */
    @Test void proactiveRevocationBackoffAndInvalidEnvelopeDoNotSpeak() throws Exception {
        try (var h = new Harness(false, true, true)) {
            active(h); var source = incoming(h, "alice", "接话候选", false); var targets = proactiveTargets(h, source); var monitor = monitor(h);
            h.turns.observe(report -> { throw new IllegalStateException("坏观察器不能影响业务终态。"); });
            try (var proactive = proactive(h, targets, monitor, h.turns)) {
                var target = targets.from(source); h.automaticAnswer.set("{\"speak\":true,\"text\":\"旧候选\"}");
                h.onModel.set(() -> h.config.setProactiveInterjectionEnabled(false));
                assertEquals(AgentRuntimeMonitor.Delivery.FAILED, proactive.candidate(target, AgentTriggerGate.Kind.INTERJECTION, source.getMessageId(), "接话候选").toCompletableFuture().join());
                h.config.setProactiveInterjectionEnabled(true); h.onModel.set(() -> { });
                assertEquals(AgentRuntimeMonitor.Delivery.SKIPPED, proactive.candidate(target, AgentTriggerGate.Kind.INTERJECTION, "新的发生", "再次候选").toCompletableFuture().join());
                assertEquals(1, h.prompts.size()); assertTrue(h.client.getSentMessages().isEmpty());
                var other = h.client.handleGroupMessage("另一群", "alice", "用户", CommandPermission.MEMBER, "另一候选").getData();
                h.automaticAnswer.set("{\"speak\":true,\"speak\":false,\"text\":\"不应发送\"}");
                assertEquals(AgentRuntimeMonitor.Delivery.FAILED, proactive.candidate(targets.from(other), AgentTriggerGate.Kind.INTERJECTION, other.getMessageId(), "另一候选").toCompletableFuture().join());
                assertTrue(h.client.getSentMessages().isEmpty());
            }
        }
    }

    /**
     * 明确启用 ACTIVE，并关闭依赖执行时刻的夜间静默供离线流程验证。
     * @param h 原夹具
     */
    private void active(Harness h) { h.config.setMode(AgentScopePolicy.Mode.ACTIVE.name()); h.config.setProactiveQuietStartHour(0); h.config.setProactiveQuietEndHour(0); }

    /**
     * 使用原真实客户端登记和 scope 解析，不从模型配置目标。
     * @param h 原夹具
     * @param source 实际原生消息
     * @return 原生目标解析器
     */
    private ProactiveTargets proactiveTargets(Harness h, ITeaNekoMessageData source) {
        var clients = new TeaNekoClientScanner(mock(IBeanScanner.class));
        clients.getHandlerMap().put(source.getClient().getClientId(), org.zexnocs.teanekocore.framework.pair.IndependentPair.of(mock(TeaNekoClient.class), source.getClient()));
        return new ProactiveTargets(clients, new TeaNekoScopeService(clients), mock(ITeaUserService.class));
    }

    /**
     * 没有外部监听器时仍记录脱敏统计。
     * @param h 原夹具
     * @return 有界监控
     */
    private AgentRuntimeMonitor monitor(Harness h) { return new AgentRuntimeMonitor(h.workers, new DefaultListableBeanFactory().getBeanProvider(IEventService.class)); }

    /**
     * 复用实际轮次、原生发送、历史和防重设施。
     * @param h 原夹具
     * @param targets 目标解析
     * @param monitor 脱敏监控
     * @param turns 实际轮次或其只读观察替身
     * @return 自有独立预算的自主服务
     */
    private ProactiveService proactive(Harness h, ProactiveTargets targets, AgentRuntimeMonitor monitor, org.zexnocs.teanekoagent.agent.IAgentTurnService turns) {
        // 2026/11/03：自主消息也逐段等待实际平台确认及回执提交。
        var service = new ProactiveService(h.configs, new AgentScopeConfigManager(), targets, h.references, h.personalities, h.history, h.attempts, turns,
                new AgentFactory(h.registry), h.registry, h.automaticAccess, h.triggers, monitor, h.timers, h.workers, h.logger, h.db.mapper);
        service.setDelivery(h.delivery);
        // 2026/11/03：复用既有自主场景核验实际共享队列，不另建用例。
        if (h.expression != null) { service.setCoordinator(h.expression.replies); }
        return service;
    }

    /**
     * 三类 Sender 经真实发送/响应服务等待原业务，保留原文授权、防重、角色及父取消边界，不产生平台回流。
     * @throws Exception 当原夹具装配或异步响应失败时
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) @Test void internalSendersCompleteThroughOriginalResponseChain() throws Exception {
        try (var h = new Harness(true, true); var operations = operations(h)) {
            var pending = new ConcurrentHashMap<UUID, CompletableFuture<ITaskResult<?>>>(); var tasks = mock(ITaskService.class);
            doAnswer(call -> {
                UUID key = call.getArgument(0); ITaskConfig<?> config = call.getArgument(1);
                var result = new CompletableFuture<ITaskResult<?>>(); pending.put(key, result);
                h.workers.execute(() -> { try { config.getCallable().call(); } catch (Exception failure) { result.completeExceptionally(failure); } });
                return new TaskFuture(h.logger, "内部业务响应", result);
            }).when(tasks).subscribeWithFuture(any(UUID.class), any(ITaskConfig.class), any(Class.class));
            doAnswer(call -> pending.get(call.getArgument(0)).complete(call.getArgument(1))).when(tasks).complete(any(), any());
            doAnswer(call -> { pending.get(call.getArgument(0)).completeExceptionally(call.getArgument(1)); return null; }).when(tasks).forceCompleteExceptionally(any(), any());
            var responses = new org.zexnocs.teanekoapp.response.ResponseService(h.logger, tasks); var events = mock(IEventService.class);
            var observed = new CopyOnWriteArrayList<org.zexnocs.teanekocore.event.interfaces.IEvent<?>>();
            doAnswer(call -> { var event = call.<org.zexnocs.teanekoapp.sender.SentEvent<?>>getArgument(0); observed.add(event); event._afterNotify(); return null; }).when(events).pushEvent(any());
            doAnswer(call -> {
                var event = call.<org.zexnocs.teanekocore.event.interfaces.IEvent<?>>getArgument(0); observed.add(event);
                if (event instanceof org.zexnocs.teanekoapp.sender.SentEvent<?> sent) { sent._afterNotify(); }
                else { responses.onResponseEvent((org.zexnocs.teanekoapp.response.ResponseEvent) event); }
                return new TaskFuture(h.logger, "内部事件回执", CompletableFuture.completedFuture(new org.zexnocs.teanekocore.actuator.task.TaskResult<>(true, null)));
            }).when(events).pushEventWithFuture(any());
            var senderService = new org.zexnocs.teanekoapp.sender.SenderService(responses, events, tasks);
            var client = new org.zexnocs.teanekoagent.client.TeaNekoAgentInternalClient(operations, events, tasks, h.db.mapper);
            var chat = new org.zexnocs.teanekoagent.sender.AgentConversationSender(senderService, client, h.db.mapper);
            var memory = new org.zexnocs.teanekoagent.sender.AgentMemoryWriteSender(senderService, client, h.db.mapper);
            var correction = new org.zexnocs.teanekoagent.sender.AgentPersonalityCorrectionSender(senderService, client, h.db.mapper);
            h.automaticAnswer.set("内部正式答案"); var source = h.source("甲", CommandPermission.MEMBER, "你好");
            var result = chat.send(source).asStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(result.isSuccess()); assertEquals("内部正式答案", Objects.requireNonNull(result.getResult()).parts().getFirst().value());
            assertFalse(chat.send(source).asStage().toCompletableFuture().join().isSuccess()); assertEquals(1, h.prompts.size());
            var remembered = memory.send(h.source("甲", CommandPermission.MEMBER, "请记住：我喜欢茶")).asStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(remembered.isSuccess()); assertNotNull(Objects.requireNonNull(remembered.getResult()).recordId());
            assertFalse(memory.send(h.source("甲", CommandPermission.MEMBER, "我喜欢茶")).asStage().toCompletableFuture().join().isSuccess());
            assertFalse(correction.send(h.source("甲", CommandPermission.MEMBER, "自然交流"), "speaking detail").asStage().toCompletableFuture().join().isSuccess());
            var corrected = correction.send(h.source("管理员", CommandPermission.OWNER, "自然交流"), "speaking detail").asStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(corrected.isSuccess()); assertTrue(Objects.requireNonNull(corrected.getResult()).added());
            try (var parent = org.zexnocs.teanekocore.actuator.execution.ExecutionControl.create()) {
                parent.cancel(); assertFalse(chat.send(h.source("甲", CommandPermission.MEMBER, "父请求已停止"), parent).asStage().toCompletableFuture().join().isSuccess());
            }
            h.config.setEnabled(false);
            assertFalse(memory.send(h.source("甲", CommandPermission.MEMBER, "请记住：不应写入")).asStage().toCompletableFuture().join().isSuccess());
            assertTrue(h.client.getSentMessages().isEmpty());
            assertTrue(observed.stream().allMatch(event -> event instanceof org.zexnocs.teanekoapp.sender.SentEvent<?> || event instanceof org.zexnocs.teanekoagent.response.AgentResponseEvent));
            assertThrows(UnsupportedOperationException.class, () -> client.send("伪造的原始请求"));
            var toolbox = new org.zexnocs.teanekoagent.client.TeaNekoAgentToolbox(chat, memory, correction, h.logger);
            assertThrows(UnsupportedOperationException.class, toolbox::getMessageSenderTools);
            assertEquals("teaneko-agent", new org.zexnocs.teanekoagent.client.TeaNekoAgentClient(client, toolbox).getClientId());
            h.config.setEnabled(true);
            doAnswer(call -> {
                call.<org.zexnocs.teanekocore.event.interfaces.IEvent<?>>getArgument(0).setCancelled(true);
                return new TaskFuture(h.logger, "已取消的发送事件", CompletableFuture.completedFuture(new org.zexnocs.teanekocore.actuator.task.TaskResult<>(true, null)));
            }).when(events).pushEventWithFuture(any());
            assertThrows(CompletionException.class, () -> chat.send(h.source("甲", CommandPermission.MEMBER, "发送事件已取消")).asStage().toCompletableFuture().join());
            assertEquals(1, h.prompts.size());
        }
    }

    /**
     * 模型可选引用、当前发起者提及和直接分段；完整信封验证失败不得发送前半段。
     * @throws Exception 当原平台夹具装配失败时
     */
    @Test void replyModesUseOnlyTrustedCurrentSource() throws Exception {
        try (var h = new Harness(false, true); var automatic = automatic(h)) {
            var receiver = receiver(h, automatic, capture(h)); var source = incoming(h, "alice", "/未知命令", true);
            h.automaticAnswer.set("{\"teaneko_reply\":1,\"messages\":[{\"mode\":\"reply\",\"text\":\"第一段\"},{\"mode\":\"at\",\"text\":\"第二段\"},{\"mode\":\"plain\",\"text\":\"第三段\"}]}");
            assertTrue(receiver.receive(source).toCompletableFuture().join());
            var sent = h.client.getSentMessages(); assertEquals(3, sent.size());
            assertTrue(sent.get(0).getMessages().stream().anyMatch(item -> item.getContentPart() instanceof org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart reply && reply.getId().equals(source.getMessageId())));
            assertTrue(sent.get(1).getMessages().stream().anyMatch(item -> item.getContentPart() instanceof org.zexnocs.teanekoapp.message.api.content.IAtTeaNekoContentPart at && at.getId().equals(source.getUserData().getUserIdInPlatform())));
            assertEquals(1, sent.get(2).getMessages().size());
            h.automaticAnswer.set("{\"teaneko_reply\":1,\"messages\":[{\"mode\":\"plain\",\"text\":\"不能先发这段\"},{\"mode\":\"at\",\"text\":\"越权目标\",\"user\":\"其他人\"}]}");
            assertThrows(CompletionException.class, () -> receiver.receive(incoming(h, "bob", "无效形式", true)).toCompletableFuture().join());
            assertEquals(3, h.client.getSentMessages().size());
            h.mediaAnswer.set(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(h.automaticAnswer.get())
                    .addImage("https://images.example/mixed.png", org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail.AUTO).build()).build());
            assertThrows(CompletionException.class, () -> receiver.receive(incoming(h, "bob", "混用控制信封与图片", true)).toCompletableFuture().join());
            h.mediaAnswer.set(null);
            h.automaticAnswer.set("{\"teaneko_reply\":1,\"messages\":[{\"mode\":\"plain\",\"text\":\"显式聊天也消费同一计划\"}]}");
            h.gateway.chat(h.source("甲", CommandPermission.MEMBER, "/agent chat 你好"), "你好", null).toCompletableFuture().join();
            assertEquals(4, h.client.getSentMessages().size());
        }
    }

    /**
     * 复用真实记忆、新人格和会话服务，不再启动旧 Runtime 或另一套存储。
     * @param h 原集成夹具
     * @return 当前测试拥有的内部业务服务
     */
    private org.zexnocs.teanekoagent.client.AgentOperations operations(Harness h) {
        var access = new org.zexnocs.teanekoagent.memory.access.MemoryAccessService(scope -> new org.zexnocs.teanekoagent.memory.access.MemoryAccessPolicy(
                h.registered.get() && h.config.isEnabled(), true, h.config.isMemoryAddEnabled(), false), h.references,
                mock(org.zexnocs.teanekoagent.memory.recall.IMemoryRecallService.class), mock(org.zexnocs.teanekoagent.memory.profile.IUserProfileService.class),
                new org.zexnocs.teanekoagent.memory.fact.MemoryFacts(h.db.facts, Clock.systemUTC()), h.workers);
        return new org.zexnocs.teanekoagent.client.AgentOperations(h.mailbox, new AgentFactory(h.registry), h.registry,
                scope -> h.registered.get() ? h.config.toPolicy() : AgentScopePolicy.unregistered(), h.references, h.bridge, h.attempts, access,
                h.personalities, (org.zexnocs.teanekoagent.personality.IPersonalityLearningTarget) h.personalities, h.workers, h.timers);
    }
    /**
     * 2026/11/03：真实模型、邮箱、历史、命令和自动入口完成同一表达分段与回执闭环。
     * @throws Exception 当原异步链未在时限内完成时
     */
    @Test void deliversExpressionPlansThroughBothNativeEntrypoints() throws Exception {
        try (var h = new Harness(false, true, true); var automatic = automatic(h)) {
            h.onModel.set(() -> {
                var json = h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages());
                var matcher = java.util.regex.Pattern.compile("TNK:([a-f0-9]{12})[|]BEGIN").matcher(json);
                assertTrue(matcher.find()); var head = "⟦TNK:" + matcher.group(1) + "|";
                h.automaticAnswer.set(head + "BEGIN|protocol=teaneko:expression|v=1⟧\n" + head + "TEXT|mode=plain|wait=0⟧\n第一段\n"
                        + head + "TEXT|mode=reply|wait=10⟧\n第二段\n" + head + "END⟧");
            });
            var source = h.source("甲", CommandPermission.MEMBER, "/agent chat 你好");
            h.gateway.chat(source, "你好", null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(2, h.client.getSentMessages().size()); assertEquals(1, h.prompts.size());
            assertFalse(h.db.mapper.writeValueAsString(history(h, source).messages()).contains("⟦TNK:"));
            assertTrue(h.db.mapper.writeValueAsString(history(h, source).messages()).contains("第二段"));
            var incoming = incoming(h, "乙", "你好", true);
            assertTrue(receiver(h, automatic).receive(incoming).toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertEquals(4, h.client.getSentMessages().size()); assertEquals(2, h.prompts.size());
            assertFalse(h.db.mapper.writeValueAsString(h.client.getSentMessages()).contains("⟦TNK:"));
        }
    }

}
