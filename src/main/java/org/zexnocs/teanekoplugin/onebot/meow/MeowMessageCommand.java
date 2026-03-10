package org.zexnocs.teanekoplugin.onebot.meow;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;

/**
 * 喵呜
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Command(value = ".*呜喵.*",
        mode = Command.CommandMode.REGEX,
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
public class MeowMessageCommand {
    @DefaultCommand
    public void meow(CommandData<ITeaNekoMessageData> commandData) {
        commandData.getRawData().getMessageSender(CommandData.getCommandToken())
                        .sendTextMessage("喵呜~");
    }
}
