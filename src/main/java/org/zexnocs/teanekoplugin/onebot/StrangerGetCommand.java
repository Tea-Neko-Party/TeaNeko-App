package org.zexnocs.teanekoplugin.onebot;

import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekoclient.onebot.utils.OnebotContentListBuilder;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

/**
 * onebot 中获取陌生人信息命令。规格：/sg <QQ号>。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Description("获取陌生人信息。规格：/sg <QQ号>")
@Command(value = {"/sg", "/陌生人"},
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
public class StrangerGetCommand {

    private final StrangerInfoGetSender strangerInfoGetSender;

    public StrangerGetCommand(StrangerInfoGetSender strangerInfoGetSender) {
        this.strangerInfoGetSender = strangerInfoGetSender;
    }

    @Description("获取陌生人信息。规格：/sg <QQ号>")
    @DefaultCommand
    public void execute(CommandData<OnebotMessageData> commandData, long userId) {
        var data = commandData.getRawData();
        strangerInfoGetSender.getPlatformUserInfo(String.valueOf(userId))
                        .thenAccept(strangerInfo -> {
                            if(strangerInfo == null) {
                                data.getMessageSender()
                                        .sendAtReplyMessage("无法获取该用户的信息！");
                                return;
                            }
                            var date = ChinaDateUtil.Instance.convertToChinaDateTime(strangerInfo.getRegTime() * 1000);
                            var regTimeStr = ChinaDateUtil.Instance.convertToString(date);
                            var messageList = OnebotContentListBuilder.builder()
                                    .addReply(data.getMessageId())
                                    .addImage(AvatarUtils.Instance.getUrl(userId))
                                    .addText(String.format("""
                                            QQ号: %d
                                            QID: %s
                                            昵称: %s
                                            性别: %s
                                            年龄: %d
                                            等级: %d
                                            个性签名: %s
                                            定义: %s
                                            大学: %s
                                            国家: %s
                                            省份: %s
                                            城市: %s
                                            地址: %s
                                            注册时间: %s
                                            生日: %d-%d-%d""",
                                            userId,
                                            strangerInfo.getQid(),
                                            strangerInfo.getNickname(),
                                            strangerInfo.getSex(),
                                            strangerInfo.getAge(),
                                            strangerInfo.getLevel(),
                                            strangerInfo.getLongNick(),
                                            strangerInfo.getPos(),
                                            strangerInfo.getCollege(),
                                            strangerInfo.getCountry(),
                                            strangerInfo.getProvince(),
                                            strangerInfo.getCity(),
                                            strangerInfo.getAddress(),
                                            regTimeStr,
                                            strangerInfo.getBirthdayYear(),
                                            strangerInfo.getBirthdayMonth(),
                                            strangerInfo.getBirthdayDay()))
                                    .build();
                            data.getMessageSender()
                                    .addMessages(messageList)
                                    .send();
                        }).finish();
    }
}
