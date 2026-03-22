package org.zexnocs.teanekoplugin.general.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃度定时器服务。
 * <br>用于存储定时器，并在启动时加载所有的定时器缓存。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
@RequiredArgsConstructor
@Service
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
