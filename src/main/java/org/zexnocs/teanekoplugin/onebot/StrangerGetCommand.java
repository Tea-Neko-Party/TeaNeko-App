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

import java.util.concurrent.CompletionStage;

/**
 * OneBot 中获取陌生人信息的命令。格式：{@code /sg <QQ号>}。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("获取陌生人信息。规格：/sg <QQ号>")
@Command(value = {"/sg", "/陌生人"},
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
public class StrangerGetCommand {

    private final StrangerInfoGetSender strangerInfoGetSender;

    /**
     * 注入平台资料查询端口，查询完成后继续原消息发送链。
     *
     * @param strangerInfoGetSender 由装配方提供的 {@code StrangerInfoGetSender} 协作依赖
     */
    public StrangerGetCommand(StrangerInfoGetSender strangerInfoGetSender) {
        this.strangerInfoGetSender = strangerInfoGetSender;
    }

    /**
     * 获取陌生人信息。规格：/sg &lt;QQ号&gt;；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("获取陌生人信息。规格：/sg <QQ号>")
    @DefaultCommand
    public CompletionStage<Void> execute(CommandData<OnebotMessageData> commandData, long userId) {
        var data = commandData.getRawData();
        return strangerInfoGetSender.getPlatformUserInfo(String.valueOf(userId))
                        .thenCompose(strangerInfo -> {
                            if(strangerInfo == null) {
                                return data.getMessageSender()
                                        .sendAtReplyMessageAsync("无法获取该用户的信息！");
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
                            return data.getMessageSender()
                                    .addMessages(messageList)
                                    .sendAsync();
                        }).asStage();
    }
}
