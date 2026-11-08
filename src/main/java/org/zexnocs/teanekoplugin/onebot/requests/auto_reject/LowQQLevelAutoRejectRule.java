package org.zexnocs.teanekoplugin.onebot.requests.auto_reject;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.request.GroupRequestEvent;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupAddRequestSender;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 低QQ等级自动拒绝入群请求。
 *
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：拒绝与报告登记完整事件阶段，所有异步副作用复核原插件代次。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 * @version 4.9.0-l
 */
@EventListener
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(
        value = "low-qq-level-auto-reject", displayName = "入群等级限制",
        description = """
        低QQ等级等级用户自动拒绝入群请求。""",
        configType = LowQQLevelAutoRejectRuleConfig.class,
        namespaces = {
                OnebotTeaNekoClient.GROUP_NAMESPACE
        })
@PluginOwned("onebot.requests")
public class LowQQLevelAutoRejectRule {
    /// 用于拒绝低级用户的发送器
    private final GroupAddRequestSender groupAddRequestSender;
    private final IConfigDataService configService;
    private final StrangerInfoGetSender strangerInfoGetSender;
    private final GroupMessageSender groupMessageSender;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final PluginAccess plugins;

    /**
     * @param groupAddRequestSender 原入群处理链
     * @param configService 原配置入口
     * @param strangerInfoGetSender 原用户资料查询
     * @param groupMessageSender 原群通知发送链
     * @param onebotScopeIdUtils 原群标识转换
     * @param plugins 插件代次入口
     */
    @Autowired
    public LowQQLevelAutoRejectRule(GroupAddRequestSender groupAddRequestSender,
                                    IConfigDataService configService,
                                    StrangerInfoGetSender strangerInfoGetSender, GroupMessageSender groupMessageSender,
                                    OnebotScopeIdUtils onebotScopeIdUtils, PluginAccess plugins) {
        this.groupAddRequestSender = groupAddRequestSender;
        this.configService = configService;
        this.strangerInfoGetSender = strangerInfoGetSender;
        this.groupMessageSender = groupMessageSender;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
        this.plugins = plugins;
    }

    /**
     * 2026/11/07：等待真实拒绝回执后才发送报告，停用撤销所有后续发送。
     * @param event 原群请求事件
     */
    @EventHandler(priority = 100000)
    public void onNewRequest(GroupRequestEvent event) {
        var token = plugins.captureOwner("onebot.requests");
        var requestData = event.getData();
        var userId = requestData.getUserId();
        var groupId = requestData.getGroupId();
        var processing = strangerInfoGetSender.getPlatformUserInfo(String.valueOf(userId)).asStage()
                .thenCompose(strangerData -> {
                    plugins.check(token);
                    // 如果无法获取用户信息或者用户等级为0，则不进行拒绝（可能是因为用户隐私设置导致无法获取信息）
                    if(strangerData == null || strangerData.getLevel() == 0) {
                        return CompletableFuture.completedStage(null);
                    }
                    var config = configService.getConfigData(this,
                            LowQQLevelAutoRejectRuleConfig.class,
                            onebotScopeIdUtils.getGroupConfigKey(groupId)).orElse(null);
                    if(config == null) {
                        // 如果没有配置数据，则不进行拒绝
                        return CompletableFuture.completedStage(null);
                    }
                    var level = strangerData.getLevel();
                    // 如果达到配置的等级阈值，则不进行拒绝
                    if(level >= config.getLevelThreshold()) {
                        return CompletableFuture.completedStage(null);
                    }
                    // 否则拒绝入群请求
                    plugins.check(token);
                    CompletionStage<Void> sends = groupAddRequestSender.rejectAsync(requestData.getFlag(),
                            config.getRejectMessage());
                    // 取消事件，防止后续的处理器继续处理这个请求
                    event.setCancelled(true);
                    // 发送拒绝消息到配置的群组
                    var list = config.getReportGroupIdList();
                    if(list == null || list.isEmpty()) {
                        return sends;
                    }
                    for(var reportGroupId: list) {
                        sends = sends.thenCompose(ignored -> {
                            plugins.check(token);
                            return groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(reportGroupId))
                                .addTextMessage(String.format("""
                                            申请加入群组 %s 被自动拒绝喵。
                                            原因：低于要求的等级 %s

                                            用户详细信息：""", groupId, config.getLevelThreshold()))
                                .addImageMessage(AvatarUtils.Instance.getUrl(userId))
                                .addTextMessage(String.format("""
                                            昵称：%s
                                            账号：%s
                                            年龄：%s
                                            等级：%s""",
                                        strangerData.getNickname(),
                                        userId,
                                        strangerData.getAge(),
                                        level))
                                .sendAsync().thenApply(sent -> null);
                        });
                    }
                    return sends;
                });
        event.trackProcessing(processing);
    }

}
