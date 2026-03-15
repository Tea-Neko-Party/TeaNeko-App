package org.zexnocs.teanekoplugin.onebot;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.notice.GroupIncreaseNoticeEvent;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.api.default_config.StringDefaultConfigData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 新成员加入时发送欢迎信息。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.3
 */
@ConfigManager(value = "member-join-welcome", description = """
        当群组有新成员加入时，发送欢迎信息。
        配置的值：欢迎信息""",
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE},
        configType = StringDefaultConfigData.class)
@EventListener
@RequiredArgsConstructor
public class MemberJoinService {
    private final GroupMessageSender groupMessageSender;
    private final IConfigDataService configService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;

    /**
     * 新成员加入时发送欢迎。
     * @param event 事件
     */
    @EventHandler
    public void onMemberJoin(GroupIncreaseNoticeEvent event) {
        var data = event.getData();
        configService.getConfigData(this, StringDefaultConfigData.class, onebotScopeIdUtils.getGroupConfigKey(data.getGroupId()))
            .ifPresent(config -> {
                var welcomeMessage = config.getValue();
                if(welcomeMessage == null || welcomeMessage.isBlank()) {
                    return;
                }
                groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(data.getGroupId()))
                        .addAtMessage(String.valueOf(data.getUserId()))
                        .addTextMessage(" " + welcomeMessage)
                        .send();
            });
    }
}
