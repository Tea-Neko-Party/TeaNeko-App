package org.zexnocs.teanekoplugin.agent.automation.schedule.adapter;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduleService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 按插件生命周期恢复持久任务；停止等待实际动作结束，重新启用重新恢复与注册。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * <br>2026/11/07：由统一插件生命周期触发可重复启停，移除独立应用就绪监听。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
// 2026/11/07：插件停用且尚无运行计时器时，总管理仍保留静态任务说明。
@TimerDisplay(category = "agent.schedule", categoryName = "Agent 计划任务", name = "持久计划扫描",
        description = "恢复并扫描到期计划，按当前创建者权限、客户端状态和动作规则执行。"
                + "停用等待已经开始的动作及持久提交结束，重新启用继续恢复历史计划；具体计划在独立页面管理。",
        controllable = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.automation")
public final class AgentScheduleLifecycle implements IPluginLifecycle {
    private final AgentScheduleService schedules;
    private final ITimerService timers;
    private boolean started;
    private CompletionStage<Void> ready = CompletableFuture.failedStage(new IllegalStateException("计划插件尚未启用。"));

    /**
     * 保存原服务，构造不访问数据库。
     * @param schedules 调度服务
     * @param timers 原 Core Timer
     */
    public AgentScheduleLifecycle(AgentScheduleService schedules, ITimerService timers) {
        this.schedules = schedules;
        this.timers = timers;
        // 2026/11/07：装配不等于启用，首次生命周期启动前拒绝后台和原生任务入口。
        schedules.stop();
    }

    /** @return 2026/11/07：此调度宿主所属业务插件。 */
    @Override
    public String pluginId() {
        return "agent.automation";
    }

    /** @return 稳定的调度生命周期标识。 */
    @Override
    public String id() {
        return "persistent-schedules";
    }

    /**
     * 2026/11/07：等待原存储恢复后发布本代定时器，失败清理后原样报告。
     * @return 恢复与注册完成阶段
     */
    @Override
    public synchronized CompletionStage<Void> start() {
        if (started) {
            return ready;
        }
        started = true;
        ready = schedules.start(timers, pluginId()).exceptionallyCompose(failure ->
                schedules.stop().thenCompose(ignored -> CompletableFuture.failedStage(failure)));
        return ready;
    }

    /**
     * 2026/11/07：先撤销原生就绪入口，再等待已受理动作与持久提交结束。
     * @return 本代调度的实际结束阶段
     */
    @Override
    public synchronized CompletionStage<Void> stop() {
        started = false;
        ready = CompletableFuture.failedStage(new IllegalStateException("计划插件已停用。"));
        return schedules.stop();
    }

    /**
     * 原生命令必须等待恢复，防止恢复过程把新创建或执行的任务当作旧任务处理。
     * @return 只读就绪阶段
     */
    public synchronized CompletionStage<Void> ready() {
        return ready;
    }
}
