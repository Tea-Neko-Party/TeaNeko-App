package org.zexnocs.teanekoplugin.onebot.requests.auto_approve;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.request.GroupRequestEvent;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupAddRequestSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekoplugin.onebot.requests.review.GroupRequestReviewService;

import java.util.concurrent.CompletableFuture;

/**
 * 根据主群自动批准子群的入群申请
 *
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：请求处理登记真实完成阶段，查询后的批准和拒绝复核原插件代次。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.3
 * @version 4.9.0-l
 */
@EventListener
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(value = "subsidiary-group-auto-approve-by-level", displayName = "子群入群审核",
               description = "根据主群中等级自动批准子群的入群申请。此外可以设定白名单和黑名单。",
               configType = SubsidiaryGroupAutoApproveByLevelRuleConfig.class,
               namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE})
@PluginOwned("onebot.requests")
public class SubsidiaryGroupAutoApproveByLevelRule {
    private final GetGroupMemberInfoSender getGroupMemberInfoSender;
    private final GroupAddRequestSender groupAddRequestSender;
    private final GroupRequestReviewService groupRequestReviewService;
    private final IConfigDataService configService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final PluginAccess plugins;

    /**
     * @param getGroupMemberInfoSender 原主群成员查询
     * @param groupRequestReviewService 原人工审核用例
     * @param groupAddRequestSender 原入群处理链
     * @param configService 原配置入口
     * @param onebotScopeIdUtils 原群标识转换
     * @param plugins 插件代次入口
     */
    @Autowired
    public SubsidiaryGroupAutoApproveByLevelRule(GetGroupMemberInfoSender getGroupMemberInfoSender,
                                                 GroupRequestReviewService groupRequestReviewService,
                                                 GroupAddRequestSender groupAddRequestSender,
                                                 IConfigDataService configService,
                                                 OnebotScopeIdUtils onebotScopeIdUtils, PluginAccess plugins) {
        this.getGroupMemberInfoSender = getGroupMemberInfoSender;
        this.groupAddRequestSender = groupAddRequestSender;
        this.groupRequestReviewService = groupRequestReviewService;
        this.configService = configService;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
        this.plugins = plugins;
    }

    /**
     * 2026/11/07：本次事件固定资格，重新启用不能恢复旧查询的后续副作用。
     * @param event 原群请求事件
     */
    @EventHandler(priority = 50000)
    public void onNewGroupRequest(GroupRequestEvent event) {
        var token = plugins.captureOwner("onebot.requests");
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
                        plugins.check(token);
                        event.trackProcessing(groupAddRequestSender.rejectAsync(flag, config.getRejectReason()));
                        event.setCancelled(true);
                    }
                    // 否则就不处理，直接返回。
                    return;
                }
                // 判断是否在白名单中。
                var whiteList = config.getWhiteList();
                if(whiteList != null && whiteList.contains(userId)) {
                    plugins.check(token);
                    event.trackProcessing(groupAddRequestSender.approveAsync(flag));
                    event.setCancelled(true);
                    return;
                }
                // 从主群中获取该用户信息，审查是否满足自动批准条件。这个是异步的。
                plugins.check(token);
                var processing = getGroupMemberInfoSender.get(
                                String.valueOf(config.getMainGroupId()),
                                String.valueOf(userId))
                    .asStage().thenCompose(memberInfo -> {
                        plugins.check(token);
                        // 根据群等级判断是否批准入群申请。
                        if(memberInfo != null && memberInfo.getLevel() != null
                                && memberInfo.getLevel() >= config.getLevelThreshold()) {
                            event.setCancelled(true);
                            return groupAddRequestSender.approveAsync(flag);
                        } else if (config.isRejectIfNotMeetLevel()) {
                            event.setCancelled(true);
                            return groupAddRequestSender.rejectAsync(flag, config.getRejectReason()).thenRun(() -> {
                                plugins.check(token);
                                groupRequestReviewService.removeRequest(groupId, userId);
                            });
                        }
                        return CompletableFuture.completedStage(null);
                    });
                event.trackProcessing(processing);
            });
    }
}
