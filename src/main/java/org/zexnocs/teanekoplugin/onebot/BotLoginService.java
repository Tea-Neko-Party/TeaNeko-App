package org.zexnocs.teanekoplugin.onebot;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.notice.BotOnlineNoticeEvent;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupListSender;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.api.default_config.StringDefaultConfigData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 当 bot 上线时进行喊话
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.3
 */
@ConfigManager(value = "bot-login", description = """
        让机器人登录时对当前群组进行喊话。
        配置的值：喊话的内容。""",
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE},
        configType = StringDefaultConfigData.class)
@EventListener
@RequiredArgsConstructor
public class BotLoginService {
    private final GroupMessageSender groupMessageSender;
    private final IConfigDataService configService;
    private final GetGroupListSender getGroupListSender;
    private final ILogger iLogger;
    private final OnebotScopeIdUtils onebotScopeIdUtils;

    /**
     * 机器人登录时发送消息。
     *
     * @param event 事件
     */
    @EventHandler
    public void onBotLogin(BotOnlineNoticeEvent event) {
        getGroupListSender.get()
            .thenAccept(result -> {
               if(!result.isSuccess()) {
                   iLogger.warn(BotLoginService.class.getName(), "获取群列表失败");
                   return;
               }
               for(var responseData: result.getResult()) {
                   var groupId = responseData.getGroupId();
                   configService.getConfigData(this,
                           StringDefaultConfigData.class,
                           onebotScopeIdUtils.getGroupConfigKey(groupId))
                   .ifPresent(config -> {
                       var message = config.getValue();
                       if (message != null && !message.isBlank()) {
                           groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(groupId))
                                   .sendTextMessage(message);
                       }
                   });
               }
            }).finish();
    }
}
