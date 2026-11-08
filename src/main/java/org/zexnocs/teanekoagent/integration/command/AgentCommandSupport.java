package org.zexnocs.teanekoagent.integration.command;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 命令异步反馈公共边界，业务失败映射与长文本发送保持一致。
 * <br>2026/11/07：供框架及独立插件命令复用，不拥有业务服务。
 * <br>2026/11/07：支持在实际反馈前复核调用资格，失效结果不再转成可发送文本。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class AgentCommandSupport {
    /** 禁止实例化公共反馈工具。 */
    private AgentCommandSupport() {}

    /**
     * 统一展开业务完成后发送反馈，异常正文及配置值不进入聊天。
     * @param command 原来源
     * @param work 非阻塞业务
     * @return 完整业务及实际发送阶段
     */
    public static CompletionStage<Void> respond(CommandData<ITeaNekoMessageData> command, Supplier<CompletionStage<String>> work) {
        return respond(command, work, () -> {});
    }

    /**
     * 2026/11/07：完成业务错误映射后再检查反馈资格，插件代次失效时停止发送。
     * @param command 原来源
     * @param work 非阻塞业务
     * @param beforeSend 实际发送前的资格复核，异常继续传播
     * @return 完整业务及实际发送阶段
     */
    public static CompletionStage<Void> respond(CommandData<ITeaNekoMessageData> command,
            Supplier<CompletionStage<String>> work, Runnable beforeSend) {
        CompletionStage<String> stage;
        try {
            stage = work.get();
        } catch (RuntimeException invalid) {
            stage = CompletableFuture.completedStage("操作未完成，请检查参数、权限、Agent 注册和当前状态。");
        }
        return stage.exceptionally(failure -> "操作未完成；已开始的事务或外部动作不表示已回滚，请核对当前状态。")
                .thenCompose(text -> {
                    beforeSend.run();
                    return send(command, text);
                });
    }

    /**
     * 长导出沿用原转发发送器，保持全部正文且不拆开 UTF-16 代理对。
     * @param command 原生来源
     * @param text 完整业务反馈
     * @return 全部分段发送完成信号
     */
    private static CompletionStage<Void> send(CommandData<ITeaNekoMessageData> command, String text) {
        var data = command.getRawData();
        if (text.length() <= 3000) {
            return data.getMessageSender().sendReplyMessageAsync(text);
        }
        var parts = new java.util.ArrayList<String>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + 3000);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return data.getClient().getTeaNekoToolbox().getMessageSenderTools().getForwardBuilder(data).addBotAllText(parts).sendByPartAsync(10);
    }
}
