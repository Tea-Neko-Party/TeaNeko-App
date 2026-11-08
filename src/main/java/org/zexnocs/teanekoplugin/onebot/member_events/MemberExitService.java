package org.zexnocs.teanekoplugin.onebot.member_events;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

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
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：退群提醒纳入事件完成阶段，停用排空等待原平台回执。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.3
 * @version 4.9.0-l
 */
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(value = "member-exit-warning", displayName = "退群提醒", description = """
        当成员在该群组中退群时，发送一条消息提醒。
        配置的值：对群组成员的称呼。
        例如 "猫猫"，默认 "成员"。""",
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE},
        configType = StringDefaultConfigData.class)
@EventListener
@RequiredArgsConstructor
@PluginOwned("onebot.member-events")
public class MemberExitService {
    private final GroupMessageSender groupMessageSender;
    private final IConfigDataService configService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final PluginAccess plugins;

    /**
     * 成员退群时发送消息。
     *
     * @param event 事件
     */
    @EventHandler
    public void onMemberExit(GroupDecreaseNoticeEvent event) {
        var token = plugins.captureOwner("onebot.member-events");
        var data = event.getData();
        configService.getConfigData(this, StringDefaultConfigData.class,
                        onebotScopeIdUtils.getGroupConfigKey(data.getGroupId()))
            .ifPresent(config -> {
                String memberName = config.getValue();
                if(memberName == null || memberName.isBlank()) {
                    memberName = "成员";
                }
                plugins.check(token);
                event.trackProcessing(groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(data.getGroupId()))
                                .addImageMessage(AvatarUtils.Instance.getUrl(data.getUserId()))
                                .addTextMessage(String.format("""
                                    有%s离开了群聊喵！
                                    离开的%s是：%s
                                    离开的原因是：%s""",
                                        memberName,
                                        memberName,
                                        data.getUserId(),
                                        data.getDecreaseReason()))
                                .sendAsync());
            });
    }
}
