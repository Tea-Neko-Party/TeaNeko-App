package org.zexnocs.teanekoapp.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.interfaces.ICommandErrorHandler;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 指令错误处理器，用于报告指令错误。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.9
 * @version 4.5.0-l
 */
@Service
public class TeaNekoCommandErrorHandler implements ICommandErrorHandler {
    private final ILogger logger;

    /**
     * 注入诊断日志端口，拒绝反馈仍使用原消息携带的发送器。
     *
     * @param logger 项目日志接口
     */
    public TeaNekoCommandErrorHandler(ILogger logger) {
        this.logger = logger;
    }

    /**
     * 判断是否可以处理该错误。
     *
     * @param data 本次处理的输入数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Override
    public CompletionStage<Void> handleMethodNotFound(CommandData<?> data) {
        return respond(data, "未找到相应的指令。");
    }

    /**
     * 在原始消息入口反馈参数错误，返回真实发送完成阶段。
     *
     * @param data 本次处理的输入数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Override
    public CompletionStage<Void> handleArgsError(CommandData<?> data) {
        return respond(data, "指令参数错误。");
    }

    /**
     * 在原始消息入口反馈权限拒绝，不启动目标处理方法。
     *
     * @param data 本次处理的输入数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Override
    public CompletionStage<Void> handleNoPermission(CommandData<?> data) {
        return respond(data, "没有权限执行该指令。");
    }

    /**
     * 反馈作用域拒绝，并将反馈发送纳入命令完成链。
     *
     * @param data 本次处理的输入数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Override
    public CompletionStage<Void> handleNotInScope(CommandData<?> data) {
        return respond(data, "指令不在作用域内。");
    }

    /**
     * 仅为显式命令发送拒绝反馈；无法关联消息时记录诊断并结束，避免普通消息误触发提示。
     *
     * @param command 带原始来源的命令数据
     * @param message 需要发送或映射的消息
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    private CompletionStage<Void> respond(CommandData<?> command, String message) {
        if (command.getBody() == null || !command.getBody().startsWith("/")) {
            return CompletableFuture.completedStage(null);
        }
        if (command.getRawData() instanceof ITeaNekoMessageData data) {
            return data.getMessageSender().sendAtReplyMessageAsync(message);
        }
        logger.errorWithReport(getClass().getSimpleName(), "指令数据不是 ITeaNekoMessageData");
        return CompletableFuture.completedStage(null);
    }
}
