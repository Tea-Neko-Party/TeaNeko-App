package org.zexnocs.teanekoplugin.agent.automation.schedule.configuration;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduleService;
import org.zexnocs.teanekoplugin.agent.automation.schedule.EasyDataScheduleStore;
import org.zexnocs.teanekoplugin.agent.automation.schedule.adapter.NativeScheduleAdapter;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;

/**
 * 安装普通提醒及专用存储，构造期间不启动平台动作；调度由应用就绪生命周期启动。
 * <br>2026/11/02：按插件归属组合任务动作并在实际副作用前复核版本。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：按动作组合插件窄策略，保留公共授权与停用后的取消能力。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.automation")
public class AgentScheduleConfig {
    /**
     * 复用 Agent 专用 EasyData 事务入口。
     * @param data 专用数据入口
     * @param mapper 现有存储转换器
     * @return 调度存储
     */
    @Bean public EasyDataScheduleStore agentScheduleStore(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        return new EasyDataScheduleStore(data, mapper);
    }

    /**
     * 只注册提醒，不给后台任务增加命令、文件或代码能力。
     * @param store 实际专用存储
     * @param adapter 当前权限及发送适配
     * @return 由 Spring 关闭的单实例调度服务
     */
    public AgentScheduleService agentScheduleService(EasyDataScheduleStore store, NativeScheduleAdapter adapter) {
        return new AgentScheduleService(store, Clock.systemUTC(), adapter, Map.of("remind", adapter));
    }

    /**
     * 2026/11/02：保留普通提醒并组合插件动作，拒绝重名，禁止另建 Timer。
     * @param store 原存储
     * @param adapter 原权限及发送适配
     * @param contributions 动作贡献
     * @param plugins 插件目录
     * @return 实际调度服务
     */
    @Bean(name = "agentScheduleService", destroyMethod = "close")
    public AgentScheduleService pluginScheduleService(EasyDataScheduleStore store, NativeScheduleAdapter adapter,
            java.util.List<org.zexnocs.teanekoplugin.agent.automation.schedule.AgentTaskAction> contributions, org.zexnocs.teanekocore.plugin.PluginRegistry plugins) {
        var actions = new java.util.TreeMap<String, AgentScheduleService.IAction>();
        actions.put("remind", adapter);
        var policies = new java.util.TreeMap<String, AgentScheduleService.IPolicy>();
        policies.put("remind", (task, operation, actor) -> {
            adapter.validateReminder(task);
            return java.util.concurrent.CompletableFuture.completedStage(null);
        });
        for (var contribution : contributions) {
            plugins.requireRegistered(contribution.pluginId());
            AgentScheduleService.IAction wrapped = (task, guard) -> plugins.invoke(contribution.pluginId(), () -> {
                var token = plugins.capture(contribution.pluginId());
                java.util.function.Supplier<java.util.concurrent.CompletionStage<Void>> checked = () -> {
                    plugins.check(token); return guard.get().thenRun(() -> plugins.check(token));
                };
                return checked.get().thenCompose(ignored -> contribution.action().execute(task, checked));
            });
            if (actions.putIfAbsent(contribution.id(), wrapped) != null) { throw new IllegalArgumentException("插件任务动作名重复：" + contribution.id()); }
            // 2026/11/05：策略也在所属插件的当前生命周期中执行，不因动作已注册而自动授权。
            policies.put(contribution.id(), (task, operation, actor) -> plugins.invoke(contribution.pluginId(), () -> {
                var token = plugins.capture(contribution.pluginId());
                return contribution.policy().check(task, operation, actor).thenRun(() -> plugins.check(token));
            }));
        }
        AgentScheduleService.IPolicy combined = (task, operation, actor) -> adapter.checkIdentity(task, operation, actor)
                .thenCompose(ignored -> {
                    if (operation == AgentScheduleService.Operation.READ || operation == AgentScheduleService.Operation.CANCEL) {
                        return java.util.concurrent.CompletableFuture.completedStage(null);
                    }
                    var policy = policies.get(task.spec().action());
                    return policy == null ? java.util.concurrent.CompletableFuture.failedStage(
                            new IllegalStateException("任务动作策略不可用。")) : policy.check(task, operation, actor);
                });
        return new AgentScheduleService(store, Clock.systemUTC(), combined, actions);
    }
}
