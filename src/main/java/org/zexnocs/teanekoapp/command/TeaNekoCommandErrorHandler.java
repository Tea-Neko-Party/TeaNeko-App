package org.zexnocs.teanekoapp.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.interfaces.ICommandErrorHandler;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 指令错误处理器，用于报告指令错误。
 *
 * @author zExNocs
 * @date 2026/02/25
 * @since 4.0.9
 */
@Service
public class TeaNekoCommandErrorHandler implements ICommandErrorHandler {
    private final ILogger logger;

    public TeaNekoCommandErrorHandler(ILogger logger) {
        this.logger = logger;
    }

    /**
     * 判断是否可以处理该错误。
     * @param commandData 指令数据
     * @return 是否可以处理
     */
    private boolean __cannotHandle(CommandData<?> commandData) {
        var body = commandData.getBody();
        return body == null || !body.startsWith("/");
    }

    /**
     * 处理指令关闭的情况。
     *
     * @param commandData 指令数据
     */
    @Override
    public void handleCommandClosed(CommandData<?> commandData) {
        // 什么也不做
    }

    /**
     * 处理没找到方法的情况。
     *
     * @param commandData 指令数据
     */
    @Override
    public void handleMethodNotFound(CommandData<?> commandData) {
        if(__cannotHandle(commandData)) {
            return;
        }
        var rawData = commandData.getRawData();
        if (rawData instanceof ITeaNekoMessageData data) {
            data.getClient()
                    .getTeaNekoToolbox()
                    .getMessageSender(CommandData.getCommandToken())
                    .sendAtReplyMessage("未找到相应的指令。", data);
        } else {
            logger.errorWithReport(this.getClass().getSimpleName(), """
                            指令数据错误，应：%s，实际：%s""".formatted(
                    ITeaNekoMessageData.class.getSimpleName(),
                    rawData.getClass().getSimpleName()));
        }
    }

    /**
     * 处理参数错误的情况
     *
     * @param commandData 指令数据
     */
    @Override
    public void handleArgsError(CommandData<?> commandData) {
        if(__cannotHandle(commandData)) {
            return;
        }
        var rawData = commandData.getRawData();
        if (rawData instanceof ITeaNekoMessageData data) {
            data.getClient()
                    .getTeaNekoToolbox()
                    .getMessageSender(CommandData.getCommandToken())
                    .sendAtReplyMessage("指令参数错误。", data);
        } else {
            logger.errorWithReport(this.getClass().getSimpleName(), """
                            指令数据错误，应：%s，实际：%s""".formatted(
                    ITeaNekoMessageData.class.getSimpleName(),
                    rawData.getClass().getSimpleName()));
        }
    }

    /**
     * 处理没有权限的情况。
     *
     * @param commandData 指令数据
     */
    @Override
    public void handleNoPermission(CommandData<?> commandData) {
        if(__cannotHandle(commandData)) {
            return;
        }
        var rawData = commandData.getRawData();
        if (rawData instanceof ITeaNekoMessageData data) {
            data.getClient()
                    .getTeaNekoToolbox()
                    .getMessageSender(CommandData.getCommandToken())
                    .sendAtReplyMessage("没有权限执行该指令。", data);
        } else {
            logger.errorWithReport(this.getClass().getSimpleName(), """
                            指令数据错误，应：%s，实际：%s""".formatted(
                    ITeaNekoMessageData.class.getSimpleName(),
                    rawData.getClass().getSimpleName()));
        }
    }

    /**
     * 处理不在作用域内的情况。
     *
     * @param commandData 指令数据
     */
    @Override
    public void handleNotInScope(CommandData<?> commandData) {
        if(__cannotHandle(commandData)) {
            return;
        }
        var rawData = commandData.getRawData();
        if (rawData instanceof ITeaNekoMessageData data) {
            data.getClient()
                    .getTeaNekoToolbox()
                    .getMessageSender(CommandData.getCommandToken())
                    .sendAtReplyMessage("指令不在作用域内。", data);
        } else {
            logger.errorWithReport(this.getClass().getSimpleName(), """
                            指令数据错误，应：%s，实际：%s""".formatted(
                    ITeaNekoMessageData.class.getSimpleName(),
                    rawData.getClass().getSimpleName()));
        }
    }
}
