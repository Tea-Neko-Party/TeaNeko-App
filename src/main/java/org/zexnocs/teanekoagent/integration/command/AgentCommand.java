package org.zexnocs.teanekoagent.integration.command;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoagent.integration.config.AgentModelSelection;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekocore.command.CommandCatalog;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.zexnocs.teanekoagent.integration.command.AgentCommandSupport.respond;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * Agent 的显式聊天、模型选择、状态和本人停止入口，统一等待业务和反馈。
 * <br>2026/11/02：为全部子命令提供 subhelp 可读取的用途和参数说明。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 * <br>2026/11/04：增加模型路由列表及 OWNER 模型切换命令。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/07：原生正文通过通用事件交给扩展识别，框架不持有业务命令端口。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "聊天助手", value = "/agent", permission = CommandPermission.ALL, scope = CommandScope.ALL)
@Description("Agent 显式聊天、模型选择与运行状态；插件按需贡献业务子命令。")
public class AgentCommand {
    private @Nullable AgentModelSelection modelSelection;

    /**
     * 2026/11/04：安装统一模型选择入口，旧独立宿主仍可使用原构造方法。
     * @param selection 当前文件路由与配置事务
     */
    @Autowired
    public void installModelSelection(AgentModelSelection selection) {
        this.modelSelection = selection;
    }

    /**
     * 2026/11/04：只读列出已应用路由，空参数不改变配置。
     * @param command 真实来源
     * @return 路由列表反馈阶段
     */
    @Description("用法：/agent models\n列出已经添加并应用的模型路由。切换模型使用 /agent model <路由>。\n示例：/agent models")
    @SubCommand("models")
    public CompletionStage<Void> models(CommandData<ITeaNekoMessageData> command) {
        return respond(command, () -> CompletableFuture.completedStage(String.join("\n",
                java.util.Objects.requireNonNull(modelSelection, "模型选择服务尚未装配。").choices().values())));
    }

    /**
     * 2026/11/04：通过版本化配置事务切换当前聊天模型。
     * @param command 真实 OWNER 来源
     * @param route 路由标识，default 表示继承文件默认
     * @return 配置提交与平台反馈阶段
     */
    @Description("用法：/agent model <路由|default>\n需要 OWNER 权限。切换这个聊天的模型；路由从 /agent models 选择，default 恢复文件默认。新请求生效。\n示例：/agent model deepseek-chat\n示例：/agent model default")
    @SubCommand(value = "model", permission = CommandPermission.OWNER)
    public CompletionStage<Void> model(CommandData<ITeaNekoMessageData> command,
                                      @Description("已添加的模型路由，default 表示继承默认") String route) {
        return respond(command, () -> java.util.Objects.requireNonNull(modelSelection, "模型选择服务尚未装配。")
                .select(command.getRawData(), route.equals("default") ? "" : route, command.getParentExecution()));
    }
    private final AgentCommandGateway gateway;
    private final @Nullable IEventService events;
    private final @Nullable AgentRuntimeMonitor monitor;
    private @Nullable CommandCatalog catalog;

    /**
     * @param gateway 原生聊天入口
     */
    public AgentCommand(AgentCommandGateway gateway) {
        this(gateway, null, null);
    }

    /**
     * 2026/11/07：框架只保留聊天运行入口，业务子命令由插件独立贡献。
     * @param gateway 原生聊天入口
     * @param events 可选原生正文扩展事件入口
     * @param monitor 当前宿主运行观测
     */
    @Autowired
    public AgentCommand(AgentCommandGateway gateway, @Nullable IEventService events,
            @Nullable AgentRuntimeMonitor monitor) {
        this.gateway = gateway;
        this.events = events;
        this.monitor = monitor;
    }

    /**
     * @param catalog 已发布的命令目录，查询时实时复核插件与权限
     */
    @Autowired
    public void installCatalog(CommandCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 2026/11/07：帮助只显示当前来源有权使用且插件仍启用的真实命令。
     * @param command 原可信命令
     * @return 帮助反馈完成阶段
     */
    @Description("显示当前可用的 Agent 子命令。")
    @DefaultCommand
    public CompletionStage<Void> help(CommandData<ITeaNekoMessageData> command) {
        var current = catalog;
        String text = current == null ? "/agent chat <文本>；/agent status；/agent stop <请求UUID>"
                : current.list(command, () -> {}).stream().filter(entry -> entry.command().equals("/agent"))
                        .map(CommandCatalog.Entry::usage)
                        .collect(Collectors.joining("\n"));
        return command.getRawData().getMessageSender().sendReplyMessageAsync(text);
    }
    /**
     * 接入真实文本请求和父链路，原命令 Stage 包含最终回复。
     * <br>2026/11/07：等待原生正文扩展的完整处理，未被认领时才进入普通对话。
     * @param command 真实来源及可选父控制
     * @param text 本次完整词列表
     * @return 对话和平台发送完成阶段
     * @throws IllegalArgumentException 当原消息与匹配命令不一致时
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent chat <文本>\n与当前聊天的 Agent 对话，正文可包含空格；使用当前配置中实际启用的组件。须先登记并启用当前聊天的 agent 配置。\n示例：/agent chat 你好，请介绍一下自己")
    @SubCommand("chat")
    public CompletionStage<Void> chat(CommandData<ITeaNekoMessageData> command, List<String> text) {
        var original = chatText(command, text);
        if (command.getParentExecution() == null && events != null) {
            var event = new AgentChatCommandEvent(command, original);
            return events.pushEventWithCompletion(event).thenCompose(ignored -> event.handled() || event.isCancelled()
                    ? CompletableFuture.completedStage(null)
                    : gateway.chat(command.getRawData(), original, null));
        }
        return gateway.chat(command.getRawData(), original, command.getParentExecution());
    }
    /**
     * 查看启用、空间及本人在途请求，不列出他人的消息。
     * @param command 原真实来源
     * @return 状态反馈完成信号
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent status\n查看当前作用域 Agent 是否登记并启用、当前记忆空间，以及本人尚未完成的聊天请求 UUID。需要停止请求时，将这里返回的请求 UUID 交给 stop。\n示例：/agent status")
    @SubCommand("status") public CompletionStage<Void> status(CommandData<ITeaNekoMessageData> command) { return respond(command, () -> gateway.status(command.getRawData())); }

    /**
     * 只允许实际 DEBUG 查看当前 scope 的统计，不列出其他作用域或原始消息。
     * @param command 原生管理命令
     * @return 查询及平台反馈完成阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent monitor\n需要 DEBUG 权限。只读查看当前作用域最近最多 100 个轮次的成功及终止计数、模型请求数、工具结果数、报告与估算 token、未知用量次数和累计耗时，以及平台交付统计；不包含对话正文或凭据。\n示例：/agent monitor")
    @SubCommand(value = "monitor", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> monitor(CommandData<ITeaNekoMessageData> command) {
        return respond(command, () -> {
            var current = monitor;
            if (current == null || command.getRawData().getUserData().getRole() != CommandPermission.DEBUG) {
                throw new IllegalStateException("监控尚未装配或当前来源不是 DEBUG。");
            }
            return CompletableFuture.completedFuture(current.describe(NativeMessageSources.actor(command.getRawData()).scope()));
        });
    }
    /**
     * 只停止当前用户自己的请求。
     * @param command 原真实来源
     * @param requestId 待停止 UUID
     * @return 取消接收反馈完成信号
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent stop <请求UUID>\n停止本人在当前作用域发起的 Agent 请求；请求 UUID 从 /agent status 获取，不是平台消息 ID。反馈表示取消请求的接收结果，已发生的外部操作不会因此撤销。\n示例：/agent stop 00000000-0000-4000-8000-000000000001\n示例 UUID 须替换为本人在途请求的实际值。")
    @SubCommand("stop") public CompletionStage<Void> stop(CommandData<ITeaNekoMessageData> command, String requestId) { return respond(command, () -> CompletableFuture.completedStage(gateway.stop(command.getRawData(), requestId))); }


    /**
     * 原生聊天只去掉命令语法前缀，保留正文内部空白；派生命令不声明原文真实性。
     * @param command 真实原生数据及匹配参数
     * @param parsed 原分词结果，仅派生调用使用
     * @return 可作为当前原文的正文或显式派生文本
     * @throws IllegalArgumentException 当原消息与已匹配命令不一致时
     */
    private String chatText(CommandData<ITeaNekoMessageData> command, List<String> parsed) {
        if (command.getParentExecution() != null) { return String.join(" ", parsed); }
        var original = command.getRawData().getMessages().stream().map(ITeaNekoContent::getContentPart).filter(ITextTeaNekoContentPart.class::isInstance)
                .map(ITextTeaNekoContentPart.class::cast).map(ITextTeaNekoContentPart::getText).collect(Collectors.joining());
        var prefix = Pattern.compile("^\\s*" + Pattern.quote(command.getBody()) + " +chat(?: +|$)").matcher(original);
        if (!prefix.find()) { throw new IllegalArgumentException("原生聊天正文与已匹配命令不一致。"); }
        return original.substring(prefix.end());
    }
}
