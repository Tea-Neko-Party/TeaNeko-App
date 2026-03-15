package org.zexnocs.teanekoplugin.onebot;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.notice.GroupDecreaseNoticeEvent;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.api.default_config.StringDefaultConfigData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 成员退群提醒。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.3
 */
@ConfigManager(value = "member-exit-warning", description = """
        当成员在该群组中退群时，发送一条消息提醒。
        配置的值：对群组成员的称呼。
        例如 "猫猫"，默认 "成员"。""",
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE},
        configType = StringDefaultConfigData.class)
@EventListener
@RequiredArgsConstructor
public class MemberExitService {
    private final GroupMessageSender groupMessageSender;
    private final IConfigDataService configService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;

    /**
     * 成员退群时发送消息。
     *
     * @param event 事件
     */
    @EventHandler
    public void onMemberExit(GroupDecreaseNoticeEvent event) {
        var data = event.getData();
        configService.getConfigData(this, StringDefaultConfigData.class,
                        onebotScopeIdUtils.getGroupConfigKey(data.getGroupId()))
            .ifPresent(config -> {
                String memberName = config.getValue();
                if(memberName == null || memberName.isBlank()) {
                    memberName = "成员";
                }
                groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(data.getGroupId()))
                                .addImageMessage(AvatarUtils.Instance.getAvatarUrl(data.getUserId()))
                                .addTextMessage(String.format("""
                                    有%s离开了猫猫茶馆喵！
                                    离开的%s是：%s
                                    离开的原因是：%s""",
                                        memberName,
                                        memberName,
                                        data.getUserId(),
                                        data.getDecreaseReason()))
                                .send();
            });
    }
}
