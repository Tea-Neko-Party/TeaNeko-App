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
import java.util.concurrent.CompletionStage;

/**
 * 个人信息服务，提供获取用户信息的功能。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Service("infoService")
public class InfoService {
    public static final String INFO_NAMESPACE = "info_service";

    private final PersonalInfoService personalInfoService;
    private final MessageBoardInfoService messageBoardInfoService;
    private final RandomUtil randomUtil;
    private final ITeaUserCoinService iTeaUserCoinService;

    /**
     * 组合简介、留言、积分与展示依赖，统一构造当前资料的发送内容。
     *
     * @param randomUtil 用于本次处理的 {@code randomUtil} 数据
     * @param personalInfoService 由装配方提供的 {@code PersonalInfoService} 协作依赖
     * @param messageBoardInfoService 由装配方提供的 {@code MessageBoardInfoService} 协作依赖
     * @param iTeaUserCoinService 本次装配使用的 {@code ITeaUserCoinService} 服务
     */
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
     * @param target 原存储协议使用的数据目标
     * @param targetId  target 平台 id
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> sentGeneralInfo(ITeaNekoMessageData data, UUID target, String targetId) {
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
            return data.getClient().getTeaNekoToolbox().getGroupMemberInfoSender()
                    .get(data.getUserData().getGroupId(), targetId)
                    .thenCompose(r -> {
                        if(r == null) {
                            return data.getMessageSender()
                                    .addReplyMessage(data.getMessageId())
                                    .addTextMessage("未找到用户信息")
                                    .sendAsync();
                        }
                        var joinTime = r.getJoinInstant() == null ?
                                "null" : ChinaDateUtil.Instance.convertToDateTimeString(r.getJoinInstant());
                        var lastSpeakTime = r.getLastSentInstant() == null ?
                                "null" : ChinaDateUtil.Instance.convertToDateTimeString(r.getLastSentInstant());
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
                        return data.getMessageSender()
                                .addMessages(builder.build())
                                .sendAsync();
                    })
                    .asStage();
        } else {
            // 否则显示个人信息
            return data.getClient().getTeaNekoToolbox().getPlatformUserGetSender()
                    .getPlatformUserInfo(targetId)
                    .thenCompose(r -> {
                        if(r == null) {
                            return data.getMessageSender()
                                    .addReplyMessage(data.getMessageId())
                                    .addTextMessage("未找到用户信息")
                                    .sendAsync();
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
                        return data.getMessageSender()
                                .addMessages(builder.build())
                                .sendAsync();
                    })
                    .asStage();
        }
    }
}
