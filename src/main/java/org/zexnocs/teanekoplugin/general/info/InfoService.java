package org.zexnocs.teanekoplugin.general.info;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserCoinService;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import org.zexnocs.teanekocore.utils.RandomUtil;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoService;
import org.zexnocs.teanekoplugin.general.info.personal.PersonalInfoService;

import java.util.UUID;

/**
 * 个人信息服务，提供获取用户信息的功能。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Service("infoService")
public class InfoService {
    public static final String INFO_NAMESPACE = "info_service";

    private final PersonalInfoService personalInfoService;
    private final MessageBoardInfoService messageBoardInfoService;
    private final RandomUtil randomUtil;
    private final ITeaUserCoinService iTeaUserCoinService;

    @Autowired
    public InfoService(RandomUtil randomUtil,
                       PersonalInfoService personalInfoService,
                       MessageBoardInfoService messageBoardInfoService, ITeaUserCoinService iTeaUserCoinService) {
        this.personalInfoService = personalInfoService;
        this.messageBoardInfoService = messageBoardInfoService;
        this.randomUtil = randomUtil;
        this.iTeaUserCoinService = iTeaUserCoinService;
    }

    /**
     * 根据
     * {@link ITeaNekoMessageData}
     * 获取用户信息并发送到群聊。
     *
     * @param data     消息数据
     * @param target    target uuid
     * @param targetId  target 平台 id
     */
    public void sentGeneralInfo(ITeaNekoMessageData data, UUID target, String targetId) {
        // 获取随机留言
        var messageList = messageBoardInfoService.getMessageList(target);
        String randomMessage;
        if (!messageList.isEmpty()) {
            var randomIndex = randomUtil.nextInt(messageList.size());
            var messageInfo = messageList.get(randomIndex);
            randomMessage = messageInfo.getMessage();
        } else {
            randomMessage = "暂无留言信息";
        }
        var coinInt = iTeaUserCoinService.getCoin(target).getCount();

        if(data.getMessageType().equals(TeaNekoMessageType.GROUP)) {
            // 如果是群组，则只显示群组消息
            data.getClient().getTeaNekoToolbox().getGroupMemberInfoSender()
                    .get(data.getUserData().getGroupId(), targetId)
                    .thenAccept(r -> {
                        if(r == null) {
                            data.getMessageSender()
                                    .addReplyMessage(data.getMessageId())
                                    .addTextMessage("未找到用户信息")
                                    .send();
                            return;
                        }
                        var joinTime = r.getJoinTimeMs() == null ?
                                "null" : ChinaDateUtil.Instance.convertToDateTimeString(r.getJoinTimeMs());
                        var lastSpeakTime = r.getLastSentTimeMs() == null ?
                                "null" : ChinaDateUtil.Instance.convertToDateTimeString(r.getLastSentTimeMs());
                        var builder = data.getClient().getTeaNekoToolbox().getMessageSenderTools().getMsgListBuilder()
                                .addReply(data.getMessageId())
                                .addText(String.format("""
                                        🍓昵称：%s
                                        🐰群昵称：%s
                                        🐱头衔：%s
                                        🐾群等级：%s
                                        🍎加入时间：%s
                                        🍌上次发言时间：%s
                                        💰猫猫币：%s 个
                                        🍮个人介绍：
                                        %s
                                        🍦随机留言：
                                        %s""",
                                        r.getNickname(),
                                        r.getCard(),
                                        r.getTitle(),
                                        r.getLevel(),
                                        joinTime,
                                        lastSpeakTime,
                                        coinInt,
                                        personalInfoService.getPersonInfo(target),
                                        randomMessage));
                        data.getMessageSender()
                                .addMessages(builder.build())
                                .send();
                    })
                    .finish();
        } else {
            // 否则显示个人信息
            data.getClient().getTeaNekoToolbox().getPlatformUserGetSender()
                    .getPlatformUserInfo(targetId)
                    .thenAccept(r -> {
                        if(r == null) {
                            data.getMessageSender()
                                    .addReplyMessage(data.getMessageId())
                                    .addTextMessage("未找到用户信息")
                                    .send();
                            return;
                        }
                        var builder = data.getClient().getTeaNekoToolbox().getMessageSenderTools().getMsgListBuilder()
                                .addReply(data.getMessageId())
                                .addText(String.format("""
                                        🐱UUID：%s
                                        🐰账号ID：%s
                                        🍓昵称：%s
                                        🐾平台等级：%d
                                        💰猫猫币：%s 个
                                        🍮个人介绍：
                                        %s
                                        🍦随机留言：
                                        %s""",
                                        target,
                                        targetId,
                                        r.getNickname(),
                                        r.getLevel(),
                                        coinInt,
                                        personalInfoService.getPersonInfo(target),
                                        randomMessage));
                        data.getMessageSender()
                                .addMessages(builder.build())
                                .send();
                    })
                    .finish();
        }
    }
}
