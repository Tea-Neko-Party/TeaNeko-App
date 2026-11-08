package org.zexnocs.teanekoplugin.general.group_management.activity;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import java.util.concurrent.CompletionStage;

import java.util.concurrent.CompletableFuture;

import org.zexnocs.teanekoplugin.support.PluginTaskScope;

import org.zexnocs.teanekocore.plugin.IPluginLifecycle;

import org.jspecify.annotations.Nullable;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃度定时器服务。
 * <br>用于存储定时器，并在启动时加载所有的定时器缓存。
 *
 * <br>2026/11/04：声明定时任务分类及网页控制边界。
 *
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 * @version 4.9.0-l
 */
@RequiredArgsConstructor
@Service
// 2026/11/04：展示声明不改变原任务调度和生命周期。
// 2026/11/04：补充任务用途、执行边界与网页控制说明。
@TimerDisplay(category = "plugin.business", categoryName = "业务插件", name = "群活跃度定时扫描",
        description = "按每个群聊配置的 cron 规则扫描成员活跃度，生成当前规则下的低活跃度统计。"
        + "扫描完成后向配置的监控群发送明细，真实查询及发送过程由原业务异步执行；群配置被撤销时不再处理。"
        + "可暂停或恢复对应规则的后续触发，已开始的扫描和已发送消息不会回滚。")
@PluginOwned("general.group-management")
public class GroupActivityCronService implements IPluginLifecycle {
    // 2026/11/07：生命周期代次与已经入队的回调绑定，重新启用不会复活旧任务。
    private @Nullable PluginTaskScope lifecycle;


    /// scope ID → cron timer
    private final Map<String, ITimerTaskConfig<?>> timerMap = new ConcurrentHashMap<>();
    private final IConfigDataService iConfigDataService;
    private final GroupActivityService groupActivityService;
    private final ITimerService iTimerService;
    private final GroupActivityQueryService groupActivityQueryService;
    private final TeaNekoScopeService teaNekoScopeService;

    /**
     * 2026/11/07：启用时重新读取当前规则，恢复未来调度而不补跑过期操作。
     * @return 全部规则完成登记的阶段
     */
    @Override
    public synchronized CompletionStage<Void> start() {
        if (lifecycle != null) {
            return CompletableFuture.completedStage(null);
        }
        lifecycle = new PluginTaskScope();
        for (var key : iConfigDataService.getAllConfigKeys(groupActivityService)) {
            updateCron(key);
        }
        return CompletableFuture.completedStage(null);
    }

    /** @return 2026/11/07：活跃度后台任务所属插件。 */
    @Override
    public String pluginId() {
        return "general.group-management";
    }

    /** @return 2026/11/07：本插件的群活跃度调度标识。 */
    @Override
    public String id() {
        return "activity-cron";
    }

    /**
     * 2026/11/07：停止本代定时器并等待实际查询及已经开始的发送。
     * @return 所有后台工作已经结束的阶段
     */
    @Override
    public synchronized CompletionStage<Void> stop() {
        var scope = lifecycle;
        lifecycle = null;
        timerMap.clear();
        return scope == null ? CompletableFuture.completedStage(null) : scope.stop();
    }

    /**
     * 2026/11/07：替换当前群的调度，停用时仅保留配置而不创建后台任务。
     * @param scopeId 群聊的完整作用域标识
     */
    public synchronized void updateCron(String scopeId) {
        var scope = lifecycle;
        if (scope == null) {
            return;
        }
        var old = timerMap.remove(scopeId);
        if (old != null) {
            scope.release(old);
        }
        var config = iConfigDataService
                .getConfigData(groupActivityService, GroupActivityConfigData.class, scopeId).orElse(null);
        if (config == null || config.getCron() == null) {
            return;
        }
        var timer = iTimerService.registerByCron(scopeId + "定时扫描活跃度", "scan-activity",
                () -> scope.execute(() -> {
                    scanAndReport(scope, scopeId, config).toCompletableFuture().join();
                    return EmptyTaskResult.INSTANCE;
                }), config.getCron(), EmptyTaskResult.getResultType());
        scope.own(timer);
        timerMap.put(scopeId, timer);
    }

    /**
     * 2026/11/07：完整等待查询与通知链，每个新发送前复核本代仍启用。
     * @param scope 本轮启用生命周期
     * @param scopeId 当前群作用域
     * @param config 本次调度读取的规则
     * @return 查询和通知全部结束的阶段
     * @throws ConfigDataNotFoundException 当前群配置已被撤销时
     */
    private CompletionStage<Void> scanAndReport(PluginTaskScope scope, String scopeId,
                                                 GroupActivityConfigData config) throws ConfigDataNotFoundException {
        var future = groupActivityService.scanWithFuture(scopeId);
        if (future == null) {
            return CompletableFuture.completedStage(null);
        }
        return future.physicalCompletion().thenCompose(ignored -> future.asStage()).thenCompose(result -> {
            scope.checkActive();
            var pair = teaNekoScopeService.fromGroupScopeId(scopeId);
            var client = pair.first();
            var messageLists = groupActivityQueryService.getAllDetail(client, pair.second(), result);
            CompletionStage<Void> sending = CompletableFuture.completedStage(null);
            for (var monitor : config.getGroups()) {
                sending = sending.thenCompose(ignored -> {
                    scope.checkActive();
                    return client.getTeaNekoToolbox().getMessageSenderTools().getGroupForwardBuilder(monitor)
                            .addBotAllList(messageLists).sendByPartAsync(8);
                });
            }
            return sending;
        }).exceptionally(failure -> {
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof ConfigDataNotFoundException) {
                return null;
            }
            throw new CompletionException(cause);
        });
    }
}
