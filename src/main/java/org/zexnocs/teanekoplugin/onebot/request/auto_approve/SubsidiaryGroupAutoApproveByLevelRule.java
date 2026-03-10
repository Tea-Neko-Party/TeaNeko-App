package org.zexnocs.teanekoplugin.onebot.request.auto_approve;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.request.GroupRequestEvent;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupAddRequestSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekoplugin.onebot.request.review.GroupRequestReviewService;

/**
 * 根据主群自动批准子群的入群申请
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.3
 */
@EventListener
@ConfigManager(value = "subsidiary-group-auto-approve-by-level",
               description = "根据主群中等级自动批准子群的入群申请。此外可以设定白名单和黑名单。",
               configType = SubsidiaryGroupAutoApproveByLevelRuleConfig.class,
               namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE})
public class SubsidiaryGroupAutoApproveByLevelRule {
    private final GetGroupMemberInfoSender getGroupMemberInfoSender;
    private final GroupAddRequestSender groupAddRequestSender;
    private final GroupRequestReviewService groupRequestReviewService;
    private final IConfigDataService configService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;

    @Autowired
    public SubsidiaryGroupAutoApproveByLevelRule(GetGroupMemberInfoSender getGroupMemberInfoSender,
                                                 GroupRequestReviewService groupRequestReviewService,
                                                 GroupAddRequestSender groupAddRequestSender,
                                                 IConfigDataService configService,
                                                 OnebotScopeIdUtils onebotScopeIdUtils) {
        this.getGroupMemberInfoSender = getGroupMemberInfoSender;
        this.groupAddRequestSender = groupAddRequestSender;
        this.groupRequestReviewService = groupRequestReviewService;
        this.configService = configService;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
    }

    @EventHandler(priority = 50000)
    public void onNewGroupRequest(GroupRequestEvent event) {
        var requestData = event.getData();
        var userId = requestData.getUserId();
        var groupId = requestData.getGroupId();
        var flag = requestData.getFlag();
        configService.getConfigData(this,
                        SubsidiaryGroupAutoApproveByLevelRuleConfig.class,
                        onebotScopeIdUtils.getGroupConfigKey(groupId))
            .ifPresent(config -> {
                // 判断是否在黑名单中。
                var blackList = config.getBlackList();
                if(blackList != null && blackList.contains(userId)) {
                    // 如果配置自动拒绝黑名单中的入群申请，则拒绝。
                    if(config.isRejectIfInBlackList()) {
                        groupAddRequestSender.reject(CommandData.getCommandToken(), flag, config.getRejectReason());
                        event.setCancelled(true);
                    }
                    // 否则就不处理，直接返回。
                    return;
                }
                // 判断是否在白名单中。
                var whiteList = config.getWhiteList();
                if(whiteList != null && whiteList.contains(userId)) {
                    groupAddRequestSender.approve(CommandData.getCommandToken(), flag);
                    event.setCancelled(true);
                    return;
                }
                // 从主群中获取该用户信息，审查是否满足自动批准条件。这个是异步的。
                getGroupMemberInfoSender.get(CommandData.getCommandToken(),
                                String.valueOf(config.getMainGroupId()),
                                String.valueOf(userId))
                    .thenAccept(memberInfo -> {
                        if(memberInfo == null) {
                            // 获取群成员信息失败，例如不在主群中，或者主群配置失败。
                            // 视为不满足自动批准条件。
                            if(config.isRejectIfNotMeetLevel()) {
                                groupAddRequestSender.reject(CommandData.getCommandToken(),
                                        flag, config.getRejectReason());
                                // 如果配置了人工审核，则将入群申请从人工审核中删除。
                                groupRequestReviewService.removeRequest(groupId, userId);
                            }
                            return;
                        }
                        if(memberInfo.getLevel() == null) {
                            // 如果群成员信息中没有等级字段，视为不满足自动批准条件。
                            if(config.isRejectIfNotMeetLevel()) {
                                groupAddRequestSender.reject(CommandData.getCommandToken(),
                                        flag, config.getRejectReason());
                                // 如果配置了人工审核，则将入群申请从人工审核中删除。
                                groupRequestReviewService.removeRequest(groupId, userId);
                            }
                            return;
                        }
                        // 根据群等级判断是否批准入群申请。
                        if(memberInfo.getLevel() >= config.getLevelThreshold()) {
                            groupAddRequestSender.approve(CommandData.getCommandToken(), flag);
                        } else if (config.isRejectIfNotMeetLevel()) {
                            groupAddRequestSender.reject(CommandData.getCommandToken(), flag, config.getRejectReason());
                            // 如果配置了人工审核，则将入群申请从人工审核中删除。
                            groupRequestReviewService.removeRequest(groupId, userId);
                        }
                    }).finish();
            });
    }
}