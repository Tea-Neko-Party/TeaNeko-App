package org.zexnocs.teanekoplugin.onebot.title;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.group.SetGroupSpecialTitleSender;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 设置 onebot 群内的头衔指令。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.1
 */
@Description("""
            设置群内的头衔。
            使用前提是机器人是群主。
            头衔里要带“猫、喵、🐱”。
            每日只能设置一次。
            可以叫管理员来帮你设置(无限制)
            样例：“/头衔 新人”，会获得“新人猫”的头衔。""")
@Command(value = {"/setTitle", "/st", "/头衔"},
        scope = CommandScope.GROUP,
        permission = CommandPermission.ALL,
        permissionPackage = "onebot.group.set-title")
public class SetSpecialTitleCommand {
    private final SetSpecialTitleService setGroupSpecialTitleService;
    // 用于强制设置头衔的发送器
    private final SetGroupSpecialTitleSender setGroupSpecialTitleSender;

    @Autowired
    public SetSpecialTitleCommand(SetSpecialTitleService setGroupSpecialTitleService,
                                  SetGroupSpecialTitleSender setGroupSpecialTitleSender) {
        this.setGroupSpecialTitleService = setGroupSpecialTitleService;
        this.setGroupSpecialTitleSender = setGroupSpecialTitleSender;
    }

    @Description("""
            设置自己的头衔。每日只能设置一次。
            如果头衔里不带“猫、喵、🐱”，则会自动在后面加一个“猫”。
            头衔总长度不能超过 6 个字符。
            样例：“/头衔 新人”，会获得“新人猫”的头衔。""")
    @DefaultCommand
    public void setGroupSpecialTitleWithLimit(CommandData<OnebotMessageData> data, String title) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        var userId = onebotData.getUserId();
        var message = setGroupSpecialTitleService.setGroupSpecialTitleWithLimit(
                CommandData.getCommandToken(), groupId, userId, title);
        if(message != null) {
            rawData.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage(message);
        }
    }

    @Description("""
            群主指令。
            可以帮别人无限制设置头衔。
            长度不能超过 6 个字符。
            样例：“/头衔 给予 @xxx 新人”，会让 xxx 获得“新人”的头衔。""")
    @SubCommand(value = {"give", "给予"}, permission = CommandPermission.DEBUG)
    public void setGroupSpecialTitleForOther(CommandData<OnebotMessageData> data, long targetId, String title) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        var message = setGroupSpecialTitleService.setGroupSpecialTitle(
                CommandData.getCommandToken(), groupId, targetId, title);
        if (message != null) {
            rawData.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage(message);
        }
    }

    @Description("""
            管理员指令。
            重置某人可以今天再次设置头衔。
            样例：“/头衔 重置 @xxx”，会让 xxx 今天可以再次设置头衔。""")
    @SubCommand(value = {"reset", "重置"}, permission = CommandPermission.ADMIN)
    public void resetGroupSpecialTitleForOther(CommandData<OnebotMessageData> data, long targetId) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        var message = setGroupSpecialTitleService.removeLimit(groupId, targetId);
        if (message != null) {
            rawData.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage(message);
        }
    }

    @Description("""
            debugger指令。
            强制设置头衔，无视六个字符的限制。""")
    @SubCommand(value = {"debug"}, permission = CommandPermission.DEBUG)
    public void setGroupSpecialTitleDebug(CommandData<OnebotMessageData> data, long targetId, String title) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        setGroupSpecialTitleSender.setGroupSpecialTitle(CommandData.getCommandToken(), groupId, targetId, title);
    }
}
