package org.zexnocs.teanekoplugin.onebot.common;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.teaneko.OnebotConnectEvent;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupListSender;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.api.default_config.StringDefaultConfigData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 当 bot 上线时进行喊话
 *
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：登录通知固定插件代次，事件完成覆盖查询和每次实际发送。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.3
 * @version 4.9.0-l
 */
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(value = "bot-login", displayName = "上线通知", description = """
        让机器人登录时对当前群组进行喊话。
        配置的值：喊话的内容。""",
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE},
        configType = StringDefaultConfigData.class)
@EventListener
@RequiredArgsConstructor
@PluginOwned("onebot.common")
public class BotLoginService {
    private final GroupMessageSender groupMessageSender;
    private final IConfigDataService configService;
    private final GetGroupListSender getGroupListSender;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final PluginAccess plugins;

    /**
     * 机器人登录时发送消息。
     *
     * @param event 事件
     */
    @EventHandler
    public void onBotLogin(OnebotConnectEvent event) {
        // 2026/11/07：后续回调复核原代次，重新启用不能恢复停用前尚未发送的通知。
        var token = plugins.captureOwner("onebot.common");
        var processing = getGroupListSender.get().asStage().thenCompose(result -> {
            plugins.check(token);
            if (!result.isSuccess()) {
                return CompletableFuture.failedStage(new IllegalStateException("上线通知获取群列表失败。"));
            }
            CompletionStage<Void> sends = CompletableFuture.completedStage(null);
            for (var responseData : result.getResult()) {
                var groupId = responseData.getGroupId();
                sends = sends.thenCompose(ignored -> {
                    plugins.check(token);
                    var config = configService.getConfigData(this, StringDefaultConfigData.class,
                            onebotScopeIdUtils.getGroupConfigKey(groupId)).orElse(null);
                    if (config == null || config.getValue() == null || config.getValue().isBlank()) {
                        return CompletableFuture.completedStage(null);
                    }
                    plugins.check(token);
                    return groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(groupId))
                            .setDelay(Duration.ofSeconds(1)).addTextMessage(config.getValue()).sendAsync()
                            .thenApply(sent -> null);
                });
            }
            return sends;
        });
        event.trackProcessing(processing);
    }
}
