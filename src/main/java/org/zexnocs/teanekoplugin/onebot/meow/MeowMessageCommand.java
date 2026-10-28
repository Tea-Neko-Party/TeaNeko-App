package org.zexnocs.teanekoplugin.onebot.meow;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;

import java.util.concurrent.CompletionStage;

/**
 * 喵呜
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Command(value = ".*呜喵.*",
        mode = Command.CommandMode.REGEX,
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
public class MeowMessageCommand {
    /**
     * 通过当前消息发送器回应文本，并将发送完成传回命令分发链。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @DefaultCommand
    public CompletionStage<Void> meow(CommandData<ITeaNekoMessageData> commandData) {
        return commandData.getRawData().getMessageSender()
                        .sendTextMessageAsync("喵呜~");
    }
}
