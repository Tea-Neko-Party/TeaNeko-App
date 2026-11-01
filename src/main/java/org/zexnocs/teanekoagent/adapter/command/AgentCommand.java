package org.zexnocs.teanekoagent.adapter.command;

import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 新 Agent 的显式聊天、状态、本人停止及空间管理入口，统一等待业务和反馈。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Command(value = "/agent", permission = CommandPermission.ALL, scope = CommandScope.ALL)
@Description("Agent 显式聊天与空间管理；使用前先登记当前作用域 agent 配置。")
public class AgentCommand {
    private final AgentCommandGateway gateway;
    /**
     * 保存应用边界，不创建实例或加载外部配置。
     * @param gateway 原生业务入口
     */
    public AgentCommand(AgentCommandGateway gateway) { this.gateway = gateway; }
    /**
     * 展示实际已经提供的入口及权限，不把未实现的管理能力列成可用命令。
     * @param command 原可信命令
     * @return 原帮助反馈完成信号
     */
    @DefaultCommand public CompletionStage<Void> help(CommandData<ITeaNekoMessageData> command) {
        return command.getRawData().getMessageSender().sendReplyMessageAsync("/agent chat <文本>\n/agent status\n/agent stop <请求UUID>\n/agent namespaces [偏移]\n/agent reset（OWNER）\n/agent setnamespace <已有空间>（OWNER）\n命令工具默认关闭，可由 OWNER 使用 /cfg set agent commandToolsEnabled true 启用。");
    }
    /**
     * 接入真实文本请求和父链路，原命令 Stage 包含最终回复。
     * @param command 真实来源及可选父控制
     * @param text 本次完整词列表
     * @return 对话和平台发送完成阶段
     * @throws IllegalArgumentException 当原消息与匹配命令不一致时
     */
    @SubCommand("chat") public CompletionStage<Void> chat(CommandData<ITeaNekoMessageData> command, List<String> text) {
        return gateway.chat(command.getRawData(), chatText(command, text), command.getParentExecution());
    }
    /**
     * 查看启用、空间及本人在途请求，不列出他人的消息。
     * @param command 原真实来源
     * @return 状态反馈完成信号
     */
    @SubCommand("status") public CompletionStage<Void> status(CommandData<ITeaNekoMessageData> command) { return respond(command, () -> gateway.status(command.getRawData())); }
    /**
     * 只停止当前用户自己的请求。
     * @param command 原真实来源
     * @param requestId 待停止 UUID
     * @return 取消接收反馈完成信号
     */
    @SubCommand("stop") public CompletionStage<Void> stop(CommandData<ITeaNekoMessageData> command, String requestId) { return respond(command, () -> CompletableFuture.completedStage(gateway.stop(command.getRawData(), requestId))); }
    /**
     * 分页列出本人创建的空间，不能指定其他用户标识。
     * @param command 原真实来源
     * @param offset 非负分页偏移
     * @return 原分页反馈完成信号
     */
    @SubCommand("namespaces") public CompletionStage<Void> namespaces(CommandData<ITeaNekoMessageData> command, @DefaultValue("0") int offset) { return respond(command, () -> gateway.namespaces(command.getRawData(), offset)); }
    /**
     * 创建并切换新空记忆空间，旧空间和人格补正保留。
     * @param command 已经原 OWNER 链授权的真实来源
     * @return 实际提交及同步观察反馈
     */
    @SubCommand(value = "reset", permission = CommandPermission.OWNER) public CompletionStage<Void> reset(CommandData<ITeaNekoMessageData> command) { return respond(command, () -> gateway.changeNamespace(command.getRawData(), null, command.getParentExecution())); }
    /**
     * 绑定已有共享空间，遵守原 OWNER 权限，不要求目标必须属于本人。
     * @param command 原授权来源
     * @param namespace 已登记空间标识
     * @return 实际提交及同步观察反馈
     */
    @SubCommand(value = "setnamespace", permission = CommandPermission.OWNER) public CompletionStage<Void> setNamespace(CommandData<ITeaNekoMessageData> command, String namespace) { return respond(command, () -> gateway.changeNamespace(command.getRawData(), namespace, command.getParentExecution())); }
    /**
     * 统一展开业务完成后发送反馈，异常正文及配置值不进入聊天。
     * @param command 原来源
     * @param work 非阻塞业务
     * @return 完整业务及实际发送阶段
     */
    private CompletionStage<Void> respond(CommandData<ITeaNekoMessageData> command, Supplier<CompletionStage<String>> work) {
        CompletionStage<String> stage;
        try { stage = work.get(); } catch (RuntimeException invalid) { stage = CompletableFuture.completedStage("操作未完成，请检查参数、权限、Agent 注册和当前状态。"); }
        return stage.exceptionally(failure -> "操作未完成；已开始的事务或外部动作不表示已回滚，请核对当前状态。")
                .thenCompose(text -> command.getRawData().getMessageSender().sendReplyMessageAsync(text));
    }
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
