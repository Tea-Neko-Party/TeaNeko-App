package org.zexnocs.teanekoplugin.onebot.common.meow;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

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
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "摸摸回应", value = ".*呜喵.*",
        mode = Command.CommandMode.REGEX,
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
@PluginOwned("onebot.common")
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
