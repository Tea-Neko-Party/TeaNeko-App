package org.zexnocs.teanekoagent.plugin;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.schedule.AgentScheduleService;
import java.util.Objects;

/**
 * 插件贡献的持久任务动作，沿用原调度、真实身份与副作用回执。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @param pluginId 所属插件
 * @param id 持久动作 ID，发布后保持兼容
 * @param policy 创建及执行时的动作参数和权限策略
 * @param action 实际异步动作
 * <br>2026/11/05：插件动作显式提供参数及权限策略，旧入口无策略时拒绝副作用。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.3-l
 */
public record AgentTaskAction(String pluginId, String id, AgentScheduleService.Action action,
                              AgentScheduleService.Policy policy) {
    /**
     * 2026/11/05：保留旧构造入口；未声明动作策略的旧扩展不能创建或执行新副作用。
     * @param pluginId 所属插件
     * @param id 动作标识
     * @param action 异步动作
     */
    public AgentTaskAction(String pluginId, String id, AgentScheduleService.Action action) {
        this(pluginId, id, action, (task, operation, actor) -> java.util.concurrent.CompletableFuture.failedStage(
                new IllegalStateException("插件任务动作未提供参数及权限策略。")));
    }
    /**
     * 校验与现有持久任务相同的动作名约束。
     * @param pluginId 所属插件
     * @param id 持久动作名
     * @param policy 动作专用策略，不替代公共身份校验
     * @param action 实际动作
     */
    public AgentTaskAction {
        Objects.requireNonNull(pluginId); Objects.requireNonNull(action); Objects.requireNonNull(policy);
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,47}")) { throw new IllegalArgumentException("插件任务动作名无效。"); }
    }
}
