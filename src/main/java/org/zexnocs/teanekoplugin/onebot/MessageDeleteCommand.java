package org.zexnocs.teanekoplugin.onebot;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContent;
import org.zexnocs.teanekoclient.onebot.core.OnebotDebuggerService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupBanSender;
import org.zexnocs.teanekoclient.onebot.sender.message.DeleteMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotGetMsgSender;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 消息撤回命令。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 */
@Description("撤回成员消息。只有拥有相关权限的人才可以使用。")
@Command(value = {"/delete", "/撤回"},
        permissionPackage = "permission.group.manager-delete",
        scope = CommandScope.GROUP,
        permission = CommandPermission.ADMIN,
        supportedClients = {OnebotTeaNekoClient.class})
public class MessageDeleteCommand {
    private final DeleteMessageSender deleteMessageSender;
    private final OnebotGetMsgSender getMsgSender;
    private final GroupBanSender groupBanSender;
    private final OnebotDebuggerService onebotDebuggerService;

    @Autowired
    public MessageDeleteCommand(DeleteMessageSender deleteMessageSender,
                                OnebotGetMsgSender getMsgSender,
                                GroupBanSender groupBanSender, OnebotDebuggerService onebotDebuggerService) {
        this.deleteMessageSender = deleteMessageSender;
        this.getMsgSender = getMsgSender;
        this.groupBanSender = groupBanSender;
        this.onebotDebuggerService = onebotDebuggerService;
    }

    @Description("将回复的消息撤回。")
    @DefaultCommand
    public void delete(CommandData<OnebotMessageData> commandData) {
        var data = commandData.getRawData();
        var messageList = data.getMessages();
        var onebotData = data.getOnebotRawMessageData();
        // 判断第一个是不是 reply
        if(messageList.isEmpty() || !(messageList.getFirst().getContent() instanceof IReplyTeaNekoContent reply)) {
            return;
        }
        // 发送撤回消息的请求
        getMsgSender.getMsg(CommandData.getCommandToken(), reply.getId())
            .thenAccept(result -> {
                if(!result.isSuccess()) {
                    data.getMessageSender(CommandData.getCommandToken())
                            .sendReplyMessage("获取消息信息失败，可能该消息已经被撤回。");
                    return;
                }
                var responseData = result.getResult();
                if(responseData == null) {
                    data.getMessageSender(CommandData.getCommandToken())
                            .sendReplyMessage("意外的错误：获取消息数据为 NULL。");
                    return;
                }
                // 如果是 debugger id，则不进行删除
                if(onebotDebuggerService.isDebugger(Long.parseLong(responseData.getUserData().getUserIdInPlatform()))) {
                    data.getMessageSender(CommandData.getCommandToken())
                            .sendAtReplyMessage("大胆喵！无耻狂徒竟敢撤回群主的消息！");
                    groupBanSender.ban(CommandData.getCommandToken(),
                            onebotData.getGroupId(), onebotData.getUserId(),
                            60);
                    return;
                }
                // 否则进行删除
                deleteMessageSender.delete(CommandData.getCommandToken(), Long.parseLong(reply.getId()));
            }).finish();
    }
}
