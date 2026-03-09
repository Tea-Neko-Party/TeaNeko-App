package org.zexnocs.teanekoplugin.onebot.request.auto_reject;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.request.GroupRequestEvent;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupAddRequestSender;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageListBuilder;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

import java.time.Duration;

/**
 * 低QQ等级自动拒绝入群请求。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 */
@EventListener
@ConfigManager(
        value = "low-qq-level-auto-reject",
        description = """
        低QQ等级等级用户自动拒绝入群请求。""",
        configType = LowQQLevelAutoRejectRuleConfig.class,
        namespaces = {
                OnebotTeaNekoClient.GROUP_NAMESPACE
        })
public class LowQQLevelAutoRejectRule {
    /// 用于拒绝低级用户的发送器
    private final GroupAddRequestSender groupAddRequestSender;
    private final IConfigDataService configService;
    private final StrangerInfoGetSender strangerInfoGetSender;
    private final GroupMessageSender groupMessageSender;

    @Autowired
    public LowQQLevelAutoRejectRule(GroupAddRequestSender groupAddRequestSender,
                                    IConfigDataService configService,
                                    StrangerInfoGetSender strangerInfoGetSender, GroupMessageSender groupMessageSender) {
        this.groupAddRequestSender = groupAddRequestSender;
        this.configService = configService;
        this.strangerInfoGetSender = strangerInfoGetSender;
        this.groupMessageSender = groupMessageSender;
    }

    @EventHandler(priority = 100000)
    public void onNewRequest(GroupRequestEvent event) {
        var requestData = event.getData();
        var userId = requestData.getUserId();
        var groupId = requestData.getGroupId();
        strangerInfoGetSender.getPlatformUserInfo(CommandData.getCommandToken(), String.valueOf(userId))
                .thenAccept(strangerData -> {
                    // 如果无法获取用户信息或者用户等级为0，则不进行拒绝（可能是因为用户隐私设置导致无法获取信息）
                    if(strangerData == null || strangerData.getLevel() == 0) {
                        return;
                    }
                    var config = configService.getConfigData(this,
                            LowQQLevelAutoRejectRuleConfig.class, String.valueOf(groupId)).orElse(null);
                    if(config == null) {
                        // 如果没有配置数据，则不进行拒绝
                        return;
                    }
                    var level = strangerData.getLevel();
                    // 如果达到配置的等级阈值，则不进行拒绝
                    if(level >= config.getLevelThreshold()) {
                        return;
                    }
                    // 否则拒绝入群请求
                    groupAddRequestSender.reject(CommandData.getCommandToken(), requestData.getFlag(),
                            config.getRejectMessage());
                    // 取消事件，防止后续的处理器继续处理这个请求
                    event.setCancelled(true);
                    // 发送拒绝消息到配置的群组
                    var list = config.getReportGroupIdList();
                    if(list == null || list.isEmpty()) {
                        return;
                    }
                    for(var reportGroupId: list) {
                        groupMessageSender.sendMessage(
                            CommandData.getCommandToken(),
                            OnebotMessageListBuilder.builder()
                                    .addTextMessage(String.format("""
                                            申请加入群组 %s 被自动拒绝喵。
                                            原因：低于要求的等级 %s
                                            
                                            用户详细信息：""", groupId, config.getLevelThreshold()))
                                    .addImageMessage(AvatarUtils.Instance.getAvatarUrl(userId))
                                    .addTextMessage(String.format("""
                                            昵称：%s
                                            账号：%s
                                            年龄：%s
                                            等级：%s""",
                                            strangerData.getNickname(),
                                            userId,
                                            strangerData.getAge(),
                                            level))
                                    .build(),
                                    String.valueOf(reportGroupId),
                                    Duration.ZERO,
                                    8,
                                    Duration.ofMillis(200)
                            );
                    }
                }).finish();
    }

}
