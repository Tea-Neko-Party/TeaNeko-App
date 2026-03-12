package org.zexnocs.teanekoplugin.general.info.messageboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.UUID;

/**
 * 留言信息指令。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@Description("获取、给予留言信息。")
@Command(value = {"/message", "/留言"},
        permission = CommandPermission.ALL,
        scope = CommandScope.GROUP,
        permissionPackage = {"info.message"})
public class MessageBoardInfoCommand {
    private final MessageBoardInfoService messageBoardInfoService;
    private final ITeaUserService iTeaUserService;

    @Autowired
    public MessageBoardInfoCommand(MessageBoardInfoService messageBoardInfoService, ITeaUserService iTeaUserService) {
        this.messageBoardInfoService = messageBoardInfoService;
        this.iTeaUserService = iTeaUserService;
    }

    @Description("""
            获取或给予留言消息。
            格式：/留言 <?目标ID> <?留言信息>
            如果留言信息为空，则获取留言信息。
            默认目标ID为当前用户""")
    @DefaultCommand
    public void getOrGive(CommandData<ITeaNekoMessageData> commandData,
                          @DefaultValue("null") String targetId,
                          List<String> messages) {
        var data = commandData.getRawData();
        var sender = data.getUserData().getUuid();
        UUID target;
        if(targetId.equals("null")) {
            target = sender;
        } else {
            target = iTeaUserService.get(data.getClient(), targetId);
        }
        if(target == null) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage("用户尚未注册喵~");
            return;
        }
        if(messages.isEmpty()) {
            var list = messageBoardInfoService.getMessageList(target);
            if(list.isEmpty()) {
                data.getMessageSender(CommandData.getCommandToken())
                        .sendReplyMessage("没有留言喵~");
                return;
            }
            var builder = data.getForwardMessageSender(CommandData.getCommandToken());
            int index = 1;
            int size = list.size();
            for(var messageData: list) {
                builder.addBotText("""
                        %s
                        索引：%d / %d""".formatted(messageData.getMessage(), index, size));
                index++;
            }
            builder.sendByPart(8);
            return;
        }
        // 否则是给予留言
        var first = messages.getFirst();
        if(first.equals("给予") || first.equals("give")) {
            data.getMessageSender(CommandData.getCommandToken()).sendReplyMessage("""
                请使用 "/留言 @ xxx" 来给别人留言。
                删除 "给予" 或者 "give" 即可。""");
            return;
        }
        var message = String.join(" ", messages);
        data.getMessageSender(CommandData.getCommandToken()).
                sendAtReplyMessage(messageBoardInfoService.setMessage(sender, target, message));
    }

    @Description("""
            debugger 指令。获取详细的留言信息。
            格式：/留言 admin-get <?目标ID>""")
    @SubCommand(value = {"admin-get"}, permission = CommandPermission.DEBUG)
    public void adminGet(CommandData<ITeaNekoMessageData> commandData,
                    @DefaultValue("0") long targetId) {
        var data = commandData.getRawData();
        UUID target;
        if(targetId == 0) {
            target = data.getUserData().getUuid();
        } else {
            target = iTeaUserService.get(data.getClient(), String.valueOf(targetId));
        }
        if(target == null) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage("用户尚未注册喵~");
            return;
        }

        var list = messageBoardInfoService.getMessageList(target);
        if(list.isEmpty()) {
            data.getMessageSender(CommandData.getCommandToken()).
                    sendReplyMessage("没有留言喵~");
            return;
        }
        var builder = data.getForwardMessageSender(CommandData.getCommandToken());
        for(var message: list) {
            var sender = data.getUserData().getUuid();
            String senderPlatformId = iTeaUserService.getPlatformId(data.getClient(), sender);
            if(senderPlatformId == null) {
                builder.addBotText("""
                        未注册该平台留言者ID：%s
                        留言内容：%s""".formatted(sender, message.getMessage()));
                continue;
            }
            builder.addText(senderPlatformId,
                    senderPlatformId,
                    """
                    留言者：%s
                    留言内容：%s""".formatted(senderPlatformId, message.getMessage()));
        }
        builder.sendByPart(8);
    }
}
