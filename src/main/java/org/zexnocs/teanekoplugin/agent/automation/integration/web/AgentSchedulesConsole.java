package org.zexnocs.teanekoplugin.agent.automation.integration.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoapp.web.client.ManagementTargets;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor;
import org.zexnocs.teanekocore.plugin.management.IManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask;
import org.zexnocs.teanekoplugin.agent.automation.schedule.EasyDataScheduleStore;
import org.zexnocs.teanekoplugin.agent.automation.schedule.ScheduleManagement;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 已解析聊天的持久计划管理贡献，使用独立管理权限，不伪造原生用户或扩大执行能力。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@Component
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.automation")
public final class AgentSchedulesConsole implements IManagementResource<ScheduleManagement.Query, Object> {
    private final EasyDataScheduleStore store;

    /** @param store 原专用事务存储 */
    public AgentSchedulesConsole(EasyDataScheduleStore store) {
        this.store = store;
    }

    /** @return 显式聊天计划管理权限 */
    @Override
    public Descriptor descriptor() {
        return new Descriptor("agent.automation", "schedules", 1, "agent.console.schedules", true, List.of());
    }

    /** @return 固定请求类型 */
    @Override
    public Class<ScheduleManagement.Query> requestType() {
        return ScheduleManagement.Query.class;
    }

    /**
     * @param context 原网关授权上下文
     * @param query 明确查询或取消
     * @return 实际持久页或原事务回执
     */
    @Override
    public CompletionStage<Object> execute(ManagementContext context, ScheduleManagement.Query query) {
        context.checkActive();
        // 2026/11/07：使用宿主已授权目标，自动化插件不依赖认知插件的管理实现。
        var target = ManagementTargets.decode(context.scopeRef());
        var scope = new AgentScope(target.client(), target.scope());
        if (!query.action().equals("READ")) {
            return store.manage(scope, context.subject(), query, context::checkActive).thenApply(value -> value);
        }
        return store.page(query.after()).thenApply(page -> {
            context.checkActive();
            var visible = page.stream().filter(task -> task.spec().creator().scope().equals(scope)).map(task ->
                    new ScheduleManagement.View(task.id(), RuntimeMonitor.safe(task.spec().title(), 128), task.spec().action(),
                            task.status().name(), task.revision(), task.nextFire(), task.spec().zoneId(), task.spec().intervalSeconds(),
                            task.lastOutcome(), Set.of(AgentScheduledTask.Status.ACTIVE, AgentScheduledTask.Status.RUNNING,
                            AgentScheduledTask.Status.PAUSED).contains(task.status()))).toList();
            return new ScheduleManagement.Page(visible, page.size() < 100 ? "" : page.getLast().id().toString());
        });
    }
}
