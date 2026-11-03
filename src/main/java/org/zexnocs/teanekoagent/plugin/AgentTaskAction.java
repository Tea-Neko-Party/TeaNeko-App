package org.zexnocs.teanekoagent.plugin;

import org.zexnocs.teanekoagent.schedule.AgentScheduleService;
import java.util.Objects;

/**
 * 插件贡献的持久任务动作，沿用原调度、真实身份与副作用回执。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param pluginId 所属插件
 * @param id 持久动作 ID，发布后保持兼容
 * @param action 实际异步动作
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
public record AgentTaskAction(String pluginId, String id, AgentScheduleService.Action action) {
    /**
     * 校验与现有持久任务相同的动作名约束。
     * @param pluginId 所属插件
     * @param id 持久动作名
     * @param action 实际动作
     */
    public AgentTaskAction {
        Objects.requireNonNull(pluginId); Objects.requireNonNull(action);
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,47}")) { throw new IllegalArgumentException("插件任务动作名无效。"); }
    }
}
