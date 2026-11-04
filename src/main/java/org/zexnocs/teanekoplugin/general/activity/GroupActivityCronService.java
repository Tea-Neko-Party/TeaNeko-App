package org.zexnocs.teanekoplugin.general.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 * @version 4.7.3-l
 */
@RequiredArgsConstructor
@Service
// 2026/11/04：展示声明不改变原任务调度和生命周期。
// 2026/11/04：补充任务用途、执行边界与网页控制说明。
@TimerDisplay(category = "plugin.business", categoryName = "业务插件", name = "群活跃度定时扫描",
        description = "按每个群聊配置的 cron 规则扫描成员活跃度，生成当前规则下的低活跃度统计。"
        + "扫描完成后向配置的监控群发送明细，真实查询及发送过程由原业务异步执行；群配置被撤销时不再处理。"
        + "可暂停或恢复对应规则的后续触发，已开始的扫描和已发送消息不会回滚。")
public class GroupActivityCronService {

    /// scope ID → cron timer
    private final Map<String, ITimerTaskConfig<?>> timerMap = new ConcurrentHashMap<>();
    private final IConfigDataService iConfigDataService;
    private final GroupActivityService groupActivityService;
    private final ITimerService iTimerService;
    private final GroupActivityQueryService groupActivityQueryService;
    private final TeaNekoScopeService teaNekoScopeService;

    /**
     * 在应用程序准备就绪后执行初始化操作。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        var keys = iConfigDataService.getAllConfigKeys(groupActivityService);
        for(var key : keys) {
            updateCron(key);
        }
    }

    /**
     * 为一个 group 更新 cron
     */
    public void updateCron(String scopeId) {
        var config = iConfigDataService
                .getConfigData(groupActivityService, GroupActivityConfigData.class, scopeId)
                .orElse(null);
        if(config == null || config.getCron() == null) {
            return;
        }
        synchronized (config) {
            var cron = config.getCron();
            var timerTaskConfig = iTimerService.registerByCron(
                    scopeId + "定时扫描活跃度",
                    "scan-activity",
                    () -> {
                        var future = groupActivityService.scanWithFuture(scopeId);
                        if(future != null) {
                            future.thenAccept(result -> {
                                var pair = teaNekoScopeService.fromGroupScopeId(scopeId);
                                var client = pair.first();
                                var groupId = pair.second();
                                var messageLists = groupActivityQueryService.getAllDetail(client, groupId, result);
                                for(var monitor : config.getGroups()) {
                                    client.getTeaNekoToolbox().getMessageSenderTools().getGroupForwardBuilder(monitor)
                                            .addBotAllList(messageLists)
                                            .sendByPart(8);
                                }
                            }).exceptionally(t -> {
                                // 循环获取异常根源
                                Throwable cause = t;
                                while(cause instanceof CompletionException && cause.getCause() != null) {
                                    cause = cause.getCause();
                                }
                                // 如果是 ConfigDataNotFoundException，说明群在注册 cron 又取消了，则跳过
                                if(cause instanceof ConfigDataNotFoundException) {
                                    return null;
                                }
                                // 否则重新抛出异常
                                throw new CompletionException(cause);
                            }).finish();
                        }
                        return EmptyTaskResult.INSTANCE;
                    },
                    cron,
                    EmptyTaskResult.getResultType());
            var old = timerMap.put(scopeId, timerTaskConfig);
            if(old != null) {
                // 删除过去的 timer
                old.setLivable(() -> false);
            }
        }
    }
}
