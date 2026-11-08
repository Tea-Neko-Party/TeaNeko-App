package org.zexnocs.teanekoplugin.onebot.requests.review;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.content.TextTeaNekoContentPart;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;
import org.zexnocs.teanekoclient.onebot.event.notice.GroupIncreaseNoticeEvent;
import org.zexnocs.teanekoclient.onebot.event.request.GroupRequestEvent;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 用于添加和删除群组请求的监听器。
 *
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：审核变更和通知等待真实阶段，异步回调持续复核原插件代次。
 *
 * @author zExNocs
 * @date 2026/03/11
 * @since 4.1.3
 * @version 4.9.0-l
 */
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(value = "group-request-review", displayName = "入群人工审核", description = """
        开启人工审核当前群组的入群请求。
        开启后，可指定审核该群组入群请求的群组 ID 列表。""",
        configType = GroupRequestReviewRuleConfig.class,
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE})
@EventListener
@PluginOwned("onebot.requests")
public class GroupRequestReviewRule {
    /// 用于发送消息到审核群
    private final GroupRequestReviewService groupRequestReviewService;

    /// 用于获取 config
    private final IConfigDataService iConfigService;
    private final StrangerInfoGetSender strangerInfoGetSender;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final GroupMessageSender groupMessageSender;
    private final PluginAccess plugins;

    /**
     * @param groupRequestReviewService 原审核用例
     * @param iConfigService 原配置入口
     * @param strangerInfoGetSender 原用户资料查询
     * @param onebotScopeIdUtils 原群标识转换
     * @param groupMessageSender 原消息发送链
     * @param plugins 插件代次复核入口
     */
    @Autowired
    public GroupRequestReviewRule(GroupRequestReviewService groupRequestReviewService,
                                  IConfigDataService iConfigService, StrangerInfoGetSender strangerInfoGetSender,
                                  OnebotScopeIdUtils onebotScopeIdUtils, GroupMessageSender groupMessageSender,
                                  PluginAccess plugins) {
        this.groupRequestReviewService = groupRequestReviewService;
        this.iConfigService = iConfigService;
        this.strangerInfoGetSender = strangerInfoGetSender;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
        this.groupMessageSender = groupMessageSender;
        this.plugins = plugins;
    }

    /**
     * 当有新的群组请求时，添加请求到服务中。
     * 如果请求的用户等级过低，则拒绝请求。
     *
     * @param event 群组请求事件
     */
    @EventHandler
    public void onNewRequest(GroupRequestEvent event) {
        var token = plugins.captureOwner("onebot.requests");
        var requestData = event.getData();
        var userId = requestData.getUserId();
        var groupId = requestData.getGroupId();
        var processing = strangerInfoGetSender.getPlatformUserInfo(
                String.valueOf(userId)).asStage().thenCompose(strangerData -> {
                plugins.check(token);
                var config = iConfigService.getConfigData(this,
                                GroupRequestReviewRuleConfig.class,
                                onebotScopeIdUtils.getGroupConfigKey(groupId))
                        .orElse(null);
                if(config == null) {
                    return CompletableFuture.completedStage(null);
                }
                // 添加请求后并取消事件
                plugins.check(token);
                groupRequestReviewService.addRequest(requestData, strangerData,
                        config.getRequestAgreeNum(), config.getRequestRejectNum());
                event.setCancelled(true);

                // 发送消息到审核群
                var list = config.getReviewGroupList();
                if(list == null || list.isEmpty()) {
                    return CompletableFuture.completedStage(null);
                }
                var messageList = groupRequestReviewService.showOneDetailRequest(groupId, userId);
                var textMessage = OnebotContent.builder()
                        .type(ITextTeaNekoContentPart.TYPE)
                        .contentPart(TextTeaNekoContentPart.builder()
                                .text("群聊 " + groupId + " 有新成员申请入群啦：\n").build())
                        .build();
                messageList.addFirst(textMessage);
                CompletionStage<Void> sends = CompletableFuture.completedStage(null);
                for(var review: list) {
                    sends = sends.thenCompose(ignored -> {
                        plugins.check(token);
                        return groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(review))
                                .addMessages(messageList).sendAsync().thenApply(sent -> null);
                    });
                }
                return sends;
        });
        event.trackProcessing(processing);
    }

    /**
     * 当非服务同意请求时，在服务里删除请求。
     *
     * @param event 成员加入事件
     */
    @EventHandler
    public void onMemberJoin(GroupIncreaseNoticeEvent event) {
        var token = plugins.captureOwner("onebot.requests");
        var data = event.getData();
        var groupId = data.getGroupId();
        var userId = data.getUserId();
        plugins.check(token);
        if(!groupRequestReviewService.removeRequest(groupId, userId)) {
            // 如果已经被服务删除了，则不提示
            return;
        }
        // 发送消息到审核群，提示已经同意该请求
        var config = iConfigService.getConfigData(this,
                        GroupRequestReviewRuleConfig.class,
                        onebotScopeIdUtils.getGroupConfigKey(groupId)).orElse(null);
        if (config == null || config.getReviewGroupList() == null) {
            return;
        }
        CompletionStage<Void> sends = CompletableFuture.completedStage(null);
        for (var review : config.getReviewGroupList()) {
            sends = sends.thenCompose(ignored -> {
                plugins.check(token);
                return groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(review))
                        .addTextMessage("""
                            请求ID：%s
                            已经被管理员同意该请求。无需再处理喵。""".formatted(userId))
                        .sendAsync().thenApply(sent -> null);
            });
        }
        event.trackProcessing(sends);
    }
}
