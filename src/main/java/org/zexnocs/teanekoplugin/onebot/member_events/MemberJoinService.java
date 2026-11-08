package org.zexnocs.teanekoplugin.onebot.member_events;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

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
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：欢迎发送纳入事件完成阶段，停用排空等待原平台回执。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.3
 * @version 4.9.0-l
 */
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(value = "member-join-welcome", displayName = "入群欢迎", description = """
        当群组有新成员加入时，发送欢迎信息。
        配置的值：欢迎信息""",
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE},
        configType = StringDefaultConfigData.class)
@EventListener
@RequiredArgsConstructor
@PluginOwned("onebot.member-events")
public class MemberJoinService {
    private final GroupMessageSender groupMessageSender;
    private final IConfigDataService configService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final PluginAccess plugins;

    /**
     * 新成员加入时发送欢迎。
     * @param event 事件
     */
    @EventHandler
    public void onMemberJoin(GroupIncreaseNoticeEvent event) {
        var token = plugins.captureOwner("onebot.member-events");
        var data = event.getData();
        configService.getConfigData(this, StringDefaultConfigData.class, onebotScopeIdUtils.getGroupConfigKey(data.getGroupId()))
            .ifPresent(config -> {
                var welcomeMessage = config.getValue();
                if(welcomeMessage == null || welcomeMessage.isBlank()) {
                    return;
                }
                plugins.check(token);
                event.trackProcessing(groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(data.getGroupId()))
                        .addAtMessage(String.valueOf(data.getUserId()))
                        .addTextMessage(" " + welcomeMessage)
                        .sendAsync());
            });
    }
}
