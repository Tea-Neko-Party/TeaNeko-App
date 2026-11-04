package org.zexnocs.teanekoplugin.onebot.delete;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.message.OnebotMessageReceiveEvent;
import org.zexnocs.teanekoclient.onebot.sender.message.DeleteMessageSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 自动撤回规则。
 * 只适用于 Onebot 群组消息。
 *
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 * @version 4.7.4-l
 */
@EventListener
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(value = "auto-delete", displayName = "自动撤回",
        description = """
        自动撤回相应成员的消息。""",
        configType = AutoDeleteRuleConfig.class,
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE})
public class AutoDeleteRule {
    private final DeleteMessageSender deleteMessageSender;
    private final IConfigDataService iConfigService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;

    @Autowired
    public AutoDeleteRule(DeleteMessageSender deleteMessageSender,
                          IConfigDataService iConfigService, OnebotScopeIdUtils onebotScopeIdUtils) {
        this.deleteMessageSender = deleteMessageSender;
        this.iConfigService = iConfigService;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
    }

    /**
     * 监听消息接收事件，判断是否需要撤回消息。
     *
     * @param event 消息接收事件
     */
    @EventHandler
    public void onMessage(OnebotMessageReceiveEvent event) {
        var data = event.getData();
        iConfigService.getConfigData(this,
                        AutoDeleteRuleConfig.class,
                        onebotScopeIdUtils.getGroupConfigKey(data.getUserData().getGroupId()))
            .ifPresent(config -> {
                var list = config.getList();
                if(list == null || list.isEmpty()) {
                    return;
                }
                if(list.contains(Long.parseLong(data.getUserData().getUserIdInPlatform()))) {
                    deleteMessageSender.delete(Long.parseLong(data.getMessageId()));
                }
            });
    }
}
