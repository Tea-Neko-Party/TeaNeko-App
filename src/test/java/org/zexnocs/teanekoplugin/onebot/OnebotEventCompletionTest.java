package org.zexnocs.teanekoplugin.onebot;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.config.TeaNekoConfigKey;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoclient.onebot.core.OnebotDebuggerService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupListResponseData;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupMemberResponseData;
import org.zexnocs.teanekoclient.onebot.data.response.params.StrangerInfoGetResponseData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekoclient.onebot.event.request.GroupRequestEvent;
import org.zexnocs.teanekoclient.onebot.event.teaneko.OnebotConnectEvent;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupListSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupAddRequestSender;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.event.core.EventScanner;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekoplugin.onebot.common.BotLoginService;
import org.zexnocs.teanekoplugin.onebot.requests.auto_approve.SubsidiaryGroupAutoApproveByLevelRule;
import org.zexnocs.teanekoplugin.onebot.requests.auto_approve.SubsidiaryGroupAutoApproveByLevelRuleConfig;
import org.zexnocs.teanekoplugin.onebot.requests.auto_reject.LowQQLevelAutoRejectRule;
import org.zexnocs.teanekoplugin.onebot.requests.auto_reject.LowQQLevelAutoRejectRuleConfig;
import org.zexnocs.teanekoplugin.onebot.requests.review.GroupRequestReviewRule;
import org.zexnocs.teanekoplugin.onebot.requests.review.GroupRequestReviewService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证平台查询之后的插件代次撤销，以及事件完成阶段覆盖真实平台回执。
 * <br>2026/11/07：停用后重新启用不能恢复原审核、拒绝、批准或上线通知。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class OnebotEventCompletionTest {
    /** 审核和低等级查询返回时已换代，不产生任何后续变更或发送。 */
    @Test
    void requestQueriesCannotResumeAfterDisableAndReenable() {
        var plugins = plugins("onebot.requests");
        var access = new PluginAccess(plugins);
        var query = new CompletableFuture<StrangerInfoGetResponseData>();
        var strangers = mock(StrangerInfoGetSender.class);
        when(strangers.getPlatformUserInfo(anyString())).thenReturn(new TaskFuture<>(mock(ILogger.class), "查询", query));
        var configs = mock(IConfigDataService.class);
        var groups = mock(GroupMessageSender.class);
        var actions = mock(GroupAddRequestSender.class);
        var reviews = mock(GroupRequestReviewService.class);
        var scope = mock(OnebotScopeIdUtils.class);
        var review = new GroupRequestReviewRule(reviews, configs, strangers, scope, groups, access);
        var low = new LowQQLevelAutoRejectRule(actions, configs, strangers, groups, scope, access);
        var reviewEvent = request();
        var lowEvent = request();
        review.onNewRequest(reviewEvent);
        low.onNewRequest(lowEvent);
        var reviewDone = reviewEvent.processingCompletion().toCompletableFuture();
        var lowDone = lowEvent.processingCompletion().toCompletableFuture();
        assertFalse(reviewDone.isDone());
        assertFalse(lowDone.isDone());
        plugins.setEnabled("onebot.requests", false);
        plugins.setEnabled("onebot.requests", true);
        query.complete(mock(StrangerInfoGetResponseData.class));
        assertThrows(ExecutionException.class, () -> reviewDone.get(1, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> lowDone.get(1, TimeUnit.SECONDS));
        verifyNoInteractions(configs, groups, actions, reviews);
    }

    /** 主群成员查询跨过停用和重新启用后，不能继续批准或删除审核记录。 */
    @Test
    void memberQueryCannotApproveWithAnOldGeneration() {
        var plugins = plugins("onebot.requests");
        var members = mock(GetGroupMemberInfoSender.class);
        var query = new CompletableFuture<GroupMemberResponseData>();
        when(members.get(anyString(), anyString())).thenReturn(new TaskFuture<>(mock(ILogger.class), "成员查询", query));
        var configs = mock(IConfigDataService.class);
        when(configs.getConfigData(any(), eq(SubsidiaryGroupAutoApproveByLevelRuleConfig.class), any(TeaNekoConfigKey.class)))
                .thenReturn(Optional.of(new SubsidiaryGroupAutoApproveByLevelRuleConfig()));
        var scope = mock(OnebotScopeIdUtils.class);
        when(scope.getGroupConfigKey(anyLong())).thenReturn(new TeaNekoConfigKey("测试群"));
        var reviews = mock(GroupRequestReviewService.class);
        var actions = mock(GroupAddRequestSender.class);
        var rule = new SubsidiaryGroupAutoApproveByLevelRule(members, reviews, actions, configs, scope,
                new PluginAccess(plugins));
        var event = request();
        rule.onNewGroupRequest(event);
        var done = event.processingCompletion().toCompletableFuture();
        assertFalse(done.isDone());
        plugins.setEnabled("onebot.requests", false);
        plugins.setEnabled("onebot.requests", true);
        query.complete(mock(GroupMemberResponseData.class));
        assertThrows(ExecutionException.class, () -> done.get(1, TimeUnit.SECONDS));
        verifyNoInteractions(actions, reviews);
    }

    /**
     * 没有报告群时也必须等待实际拒绝完成，不能只等待陌生人查询。
     * @throws Exception 当测试回执未按期限完成时
     */
    @Test
    void eventCompletionIncludesThePlatformRejection() throws Exception {
        var plugins = plugins("onebot.requests");
        var strangers = mock(StrangerInfoGetSender.class);
        var stranger = mock(StrangerInfoGetResponseData.class);
        when(stranger.getLevel()).thenReturn(1);
        when(strangers.getPlatformUserInfo(anyString())).thenReturn(new TaskFuture<>(mock(ILogger.class), "查询",
                CompletableFuture.completedFuture(stranger)));
        var configs = mock(IConfigDataService.class);
        var config = new LowQQLevelAutoRejectRuleConfig((short) 10, "测试等级不足", List.of());
        when(configs.getConfigData(any(), eq(LowQQLevelAutoRejectRuleConfig.class), any(TeaNekoConfigKey.class)))
                .thenReturn(Optional.of(config));
        var scope = mock(OnebotScopeIdUtils.class);
        when(scope.getGroupConfigKey(anyLong())).thenReturn(new TeaNekoConfigKey("测试群"));
        var actions = mock(GroupAddRequestSender.class);
        var acknowledgement = new CompletableFuture<Void>();
        when(actions.rejectAsync(anyString(), anyString())).thenReturn(acknowledgement);
        var groups = mock(GroupMessageSender.class);
        var rule = new LowQQLevelAutoRejectRule(actions, configs, strangers, groups, scope, new PluginAccess(plugins));
        var event = request();
        rule.onNewRequest(event);
        var done = event.processingCompletion().toCompletableFuture();
        assertFalse(done.isDone());
        acknowledgement.complete(null);
        done.get(1, TimeUnit.SECONDS);
        verifyNoInteractions(groups);
    }

    /** 上线查询尚未完成时停用，迟到群列表不能发送上线消息。 */
    @Test
    void loginQueryCannotSendAfterRevocation() {
        var plugins = plugins("onebot.common");
        var lists = mock(GetGroupListSender.class);
        var query = new CompletableFuture<ITaskResult<List<GroupListResponseData>>>();
        when(lists.get()).thenReturn(new TaskFuture<>(mock(ILogger.class), "群列表", query));
        var groups = mock(GroupMessageSender.class);
        var configs = mock(IConfigDataService.class);
        var rule = new BotLoginService(groups, configs, lists, mock(OnebotScopeIdUtils.class), new PluginAccess(plugins));
        var event = new OnebotConnectEvent("测试连接");
        rule.onBotLogin(event);
        var done = event.processingCompletion().toCompletableFuture();
        assertFalse(done.isDone());
        plugins.setEnabled("onebot.common", false);
        plugins.setEnabled("onebot.common", true);
        query.complete(new TaskResult<>(true, List.of()));
        assertThrows(ExecutionException.class, () -> done.get(1, TimeUnit.SECONDS));
        verifyNoInteractions(groups, configs);
    }

    /**
     * @param id 本例所属插件
     * @return 具有真实代次检查的隔离目录
     */
    private static PluginRegistry plugins(String id) {
        var plugins = new PluginRegistry(List.of(() -> new PluginDescriptor(id, "1", 1, Set.of())));
        plugins.setEnabled(id, true);
        return plugins;
    }

    /** @return 使用实际事件解析与通用完成登记的固定请求 */
    private static GroupRequestEvent request() {
        var shared = new OnebotEventShareComponent(mock(ILogger.class), mock(EventScanner.class), new ObjectMapper(),
                mock(OnebotTeaNekoClient.class), mock(ITeaUserService.class), mock(IEventService.class),
                mock(TeaNekoCommandConverter.class), mock(IFileConfigService.class), mock(OnebotDebuggerService.class));
        return new GroupRequestEvent("{\"user_id\":1,\"group_id\":2,\"flag\":\"测试请求\"}", shared);
    }
}
