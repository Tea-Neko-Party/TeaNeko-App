package org.zexnocs.teanekoplugin.onebot.request.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContent;
import org.zexnocs.teanekoapp.message.content.TextTeaNekoContent;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;
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

/**
 * 用于添加和删除群组请求的监听器。
 *
 * @author zExNocs
 * @date 2026/03/11
 * @since 4.1.3
 */
@ConfigManager(value = "group-request-review", description = """
        开启人工审核当前群组的入群请求。
        开启后，可指定审核该群组入群请求的群组 ID 列表。""",
        configType = GroupRequestReviewRuleConfig.class,
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE})
@EventListener
public class GroupRequestReviewRule {
    /// 用于发送消息到审核群
    private final GroupRequestReviewService groupRequestReviewService;

    /// 用于获取 config
    private final IConfigDataService iConfigService;
    private final StrangerInfoGetSender strangerInfoGetSender;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final GroupMessageSender groupMessageSender;

    @Autowired
    public GroupRequestReviewRule(GroupRequestReviewService groupRequestReviewService,
                                  IConfigDataService iConfigService, StrangerInfoGetSender strangerInfoGetSender, OnebotScopeIdUtils onebotScopeIdUtils, GroupMessageSender groupMessageSender) {
        this.groupRequestReviewService = groupRequestReviewService;
        this.iConfigService = iConfigService;
        this.strangerInfoGetSender = strangerInfoGetSender;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
        this.groupMessageSender = groupMessageSender;
    }

    /**
     * 当有新的群组请求时，添加请求到服务中。
     * 如果请求的用户等级过低，则拒绝请求。
     *
     * @param event 群组请求事件
     */
    @EventHandler
    public void onNewRequest(GroupRequestEvent event) {
        var requestData = event.getData();
        var userId = requestData.getUserId();
        var groupId = requestData.getGroupId();
        strangerInfoGetSender.getPlatformUserInfo(
                String.valueOf(userId)).thenAccept(strangerData -> {
                var config = iConfigService.getConfigData(this,
                                GroupRequestReviewRuleConfig.class,
                                onebotScopeIdUtils.getGroupConfigKey(groupId))
                        .orElse(null);
                if(config == null) {
                    return;
                }
                // 添加请求后并取消事件
                groupRequestReviewService.addRequest(requestData, strangerData,
                        config.getRequestAgreeNum(), config.getRequestRejectNum());
                event.setCancelled(true);

                // 发送消息到审核群
                var list = config.getReviewGroupList();
                if(list == null || list.isEmpty()) {
                    return;
                }
                var messageList = groupRequestReviewService.showOneDetailRequest(groupId, userId);
                var textMessage = OnebotMessage.builder()
                        .type(ITextTeaNekoContent.TYPE)
                        .content(TextTeaNekoContent.builder()
                                .text("群聊 " + groupId + " 有新成员申请入群啦：\n").build())
                        .build();
                messageList.addFirst(textMessage);
                for(var review: list) {
                    groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(review))
                                    .addMessages(messageList)
                                    .send();
                }
        });
    }

    /**
     * 当非服务同意请求时，在服务里删除请求。
     *
     * @param event 成员加入事件
     */
    @EventHandler
    public void onMemberJoin(GroupIncreaseNoticeEvent event) {
        var data = event.getData();
        var groupId = data.getGroupId();
        var userId = data.getUserId();
        if(!groupRequestReviewService.removeRequest(groupId, userId)) {
            // 如果已经被服务删除了，则不提示
            return;
        }
        // 发送消息到审核群，提示已经同意该请求
        iConfigService.getConfigData(this,
                        GroupRequestReviewRuleConfig.class,
                        onebotScopeIdUtils.getGroupConfigKey(groupId))
            .ifPresent(config -> {
                var list = config.getReviewGroupList();
                if(list == null || list.isEmpty()) {
                    return;
                }
                for(var review: list) {
                    groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(), String.valueOf(review))
                            .sendTextMessage("""
                                请求ID：%s
                                已经被管理员同意该请求。无需再处理喵。""");
                }
            });
    }
}
