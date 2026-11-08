package org.zexnocs.teanekoagent.integration.message;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.message.api.content.IAtTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekocore.command.CommandScanner;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 唯一的新 Agent 普通消息入口，先检查注册和原生信号，再借用原业务域执行。
 * 不取消其他插件事件，不接收内部 Agent 消息，不因文本里的身份声明授予触发权限。
 * <br>2026/11/02：私聊普通图文使用独立原生信号直接触发。
 * <br>2026/11/03：入站失败补充不含原文和密钥的异常类型及代码位置。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：向原消息事件登记实际回复完成阶段，允许网页观察异步失败。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：通过可选采集端口调用插件业务，纯模型入站不依赖记忆实现。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@EventListener
public final class NativeAgentReceiver {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final AgentTriggerGate triggers;
    private final AgentAutomaticGateway gateway;
    private final TeaNekoCommandConverter converter;
    private final CommandScanner commands;
    private final Executor executor;
    private final ILogger logger;
    private final @Nullable IPlatformHistoryCapture capture;
    private final @Nullable IAgentProactiveCoordinator proactive;

    /**
     * 组合既有配置、命令识别和业务执行器，不在事件通知线程等待模型。
     * @param configs 当前作用域配置
     * @param manager Agent 注册声明
     * @param triggers 当前触发门控
     * @param gateway 自动回复应用边界
     * @param converter 原生命令转换
     * @param commands 实际命令目录
     * @param executor 借用业务执行器
     * @param logger 固定失败日志
     */
    public NativeAgentReceiver(IConfigDataService configs, AgentScopeConfigManager manager, AgentTriggerGate triggers,
            AgentAutomaticGateway gateway, TeaNekoCommandConverter converter, CommandScanner commands,
            @Qualifier("virtualExecutor") Executor executor, ILogger logger) {
        this(configs, manager, triggers, gateway, converter, commands, executor, logger, null);
    }

    /**
     * 装配原生采集；旧独立宿主可继续使用不采集的构造入口。
     * @param configs 当前配置
     * @param manager 注册声明
     * @param triggers 触发策略
     * @param gateway 自动轮次入口
     * @param converter 原命令转换
     * @param commands 原命令目录
     * @param executor 借用业务域
     * @param logger 日志
     * @param capture 原生历史采集
     */
    public NativeAgentReceiver(IConfigDataService configs, AgentScopeConfigManager manager, AgentTriggerGate triggers,
            AgentAutomaticGateway gateway, TeaNekoCommandConverter converter, CommandScanner commands,
            @Qualifier("virtualExecutor") Executor executor, ILogger logger, @Nullable IPlatformHistoryCapture capture) {
        this(configs, manager, triggers, gateway, converter, commands, executor, logger, capture, null);
    }

    /**
     * 组合被动回复、独立采集和可选自主接话，三者共享原生身份而不共享用户权限。
     * @param configs 当前配置
     * @param manager 注册声明
     * @param triggers 原触发策略
     * @param gateway 被动入口
     * @param converter 命令转换
     * @param commands 命令目录
     * @param executor 借用业务域
     * @param logger 日志
     * @param capture 可选采集
     * @param proactive 可选自主协调
     */
    @Autowired public NativeAgentReceiver(IConfigDataService configs, AgentScopeConfigManager manager, AgentTriggerGate triggers,
            AgentAutomaticGateway gateway, TeaNekoCommandConverter converter, CommandScanner commands,
            @Qualifier("virtualExecutor") Executor executor, ILogger logger, @Nullable IPlatformHistoryCapture capture, @Nullable IAgentProactiveCoordinator proactive) {
        this.configs = configs; this.manager = manager; this.triggers = triggers; this.gateway = gateway;
        this.converter = converter; this.commands = commands; this.executor = executor; this.logger = logger;
        this.capture = capture;
        this.proactive = proactive;
    }

    /**
     * 原命令仍由原入口处理，自动链失败仅记固定诊断，不向用户重复发送错误反馈。
     * 2026/11/03：保留脱敏根因，避免只有泛化失败文本而无法定位。
     * @param event 原生普通消息事件
     */
    @EventHandler(priority = Integer.MIN_VALUE + 1)
    public void onMessage(TeaNekoMessageReceiveEvent<?> event) {
        var data = event.getData();
        if (data == null || event.isCancelled()) { return; }
        // 2026/11/04：登记真实模型与交付链，原事件通知线程继续保持非阻塞。
        var work = receive(data);
        event.trackProcessing(work);
        work.whenComplete((sent, failure) -> {
            if (failure != null) { logger.error("Agent 自动入站", "自动消息未完成；已开始的外部动作不表示已撤销，不自动重试。"
                    + org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure)); }
        });
    }

    /**
     * 完整原生入站，未登记不初始化空间；旁观采集不启动模型。
     * @param data 原生消息
     * @return 实际回复全部交付时为真，未触发或无发言为假
     */
    public CompletionStage<Boolean> receive(ITeaNekoMessageData data) {
        return CompletableFuture.supplyAsync(() -> {
            var config = configs.getConfigData(manager, AgentScopeConfigData.class, data.getScopeId()).orElse(null);
            if (config == null || !config.isEnabled() || data.getMessageType() == TeaNekoMessageType.OTHER) { return null; }
            var self = data.getClient().getTeaNekoToolbox().getSelfPlatformId();
            if (self.isEmpty() || self.get().equals(data.getUserData().getUserIdInPlatform())) { return null; }
            var text = data.getMessages().stream().map(ITeaNekoContent::getContentPart).filter(ITextTeaNekoContentPart.class::isInstance)
                    .map(ITextTeaNekoContentPart.class::cast).map(ITextTeaNekoContentPart::getText).collect(Collectors.joining(""));
            if (text.length() > 65536 || isCommand(data, text)) { return null; }
            var payload = NativeMessageInput.from(data);
            if (!payload.hasContent()) { return null; }
            boolean mention = data.getMessages().stream().map(ITeaNekoContent::getContentPart)
                    .filter(IAtTeaNekoContentPart.class::isInstance).map(IAtTeaNekoContentPart.class::cast).anyMatch(at -> self.get().equals(at.getId()));
            boolean named = Arrays.stream(config.getTriggerNames().split(",", -1)).map(String::strip).filter(name -> !name.isEmpty())
                    .anyMatch(name -> text.stripLeading().startsWith(name) && (text.stripLeading().length() == name.length()
                            || Character.isWhitespace(text.stripLeading().charAt(name.length())) || "，,:：！!？?".indexOf(text.stripLeading().charAt(name.length())) >= 0));
            return new Input(payload, self.get(), mention, named);
        }, executor).thenCompose(input -> {
            if (input == null) { return CompletableFuture.completedFuture(false); }
            var history = capture == null ? CompletableFuture.completedFuture(new IPlatformHistoryCapture.Capture(null, true))
                    : capture.capture(data, input.payload());
            return history.thenCompose(collected -> {
            if (!collected.accepted()) { return CompletableFuture.completedFuture(false); }
            return replyToSelf(data, input).thenComposeAsync(reply -> {
                var ticket = triggers.acquire(new AgentTriggerGate.Signal(NativeMessageSources.actor(data), AgentTriggerGate.Kind.MESSAGE,
                        // 2026/11/02：群聊保留原提及/引用规则，私聊无需提及。
                        input.mention(), reply, input.named(), false, data.getMessageType() == TeaNekoMessageType.PRIVATE || data.getMessageType() == TeaNekoMessageType.PRIVATE_TEMP));
                if (ticket.isEmpty()) { return proactive == null ? CompletableFuture.completedFuture(false) : proactive.offer(data, input.payload()); }
                if (proactive != null) { proactive.observe(data); }
                return gateway.respond(data, input.payload(), ticket.get(), collected.context()).whenComplete((sent, failure) -> {
                    try { triggers.finish(ticket.get(), failure == null && Boolean.TRUE.equals(sent)); }
                    finally { triggers.finish(ticket.get(), false); }
                });
            }, executor);
            });
        });
    }

    /**
     * 与原分发器使用相同规范化正文匹配，原有命令无论是否可执行都不再触发模型。
     * @param data 原生消息
     * @param text 原生字面正文，排除提及机器人导致的命令首参数偏移
     * @return 是否属于实际命令目录
     */
    private boolean isCommand(ITeaNekoMessageData data, String text) {
        var command = converter.__parse(data);
        if (command == null) { return false; }
        if (commands.getPrefixCommand(command.getBody()) != null) { return true; }
        var body = command.getBody() + (command.getArgs().length == 0 ? "" : " " + String.join(" ", command.getArgs()));
        if (commands.getRegexpCommandKeys().stream().anyMatch(body::matches)) { return true; }
        var words = new org.zexnocs.teanekoapp.message.content.TextTeaNekoContentPart(text).toCommandArgs();
        return words.length > 0 && (commands.getPrefixCommand(words[0]) != null
                || commands.getRegexpCommandKeys().stream().anyMatch(String.join(" ", words)::matches));
    }

    /**
     * 引用只验证原作者、客户端、scope 和 ID，不把引用正文交给模型或授权判定。
     * @param data 原生消息
     * @param input 固定原生解析
     * @return 可信引用机器人时为真；补取失败不增加权限
     */
    private CompletionStage<Boolean> replyToSelf(ITeaNekoMessageData data, Input input) {
        if (input.mention()) { return CompletableFuture.completedFuture(false); }
        var ids = data.getMessages().stream().map(ITeaNekoContent::getContentPart).filter(IReplyTeaNekoContentPart.class::isInstance)
                .map(IReplyTeaNekoContentPart.class::cast).map(IReplyTeaNekoContentPart::getId).distinct().toList();
        if (ids.size() != 1) { return CompletableFuture.completedFuture(false); }
        try {
            return data.getClient().getTeaNekoToolbox().getGetMsgSender().getMsg(ids.getFirst()).asStage().toCompletableFuture()
                    .orTimeout(10, TimeUnit.SECONDS).handle((result, failure) -> {
                        var quoted = failure == null && result != null && result.isSuccess() ? result.getResult() : null;
                        return quoted != null && ids.getFirst().equals(quoted.getMessageId()) && quoted.getScopeId().equals(data.getScopeId())
                                && quoted.getClient().getClientId().equals(data.getClient().getClientId())
                                && input.self().equals(quoted.getUserData().getUserIdInPlatform());
                    });
        } catch (RuntimeException unavailable) { return CompletableFuture.completedFuture(false); }
    }

    /**
     * 当前原文和原生信号的短暂解析，不包含平台句柄或引用正文。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param payload 当前冻结图文与平台时间
     * @param self 可信机器人平台 ID
     * @param mention 原生提及
     * @param named 显式配置称呼
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    private record Input(NativeMessageInput payload, String self, boolean mention, boolean named) { }
}
