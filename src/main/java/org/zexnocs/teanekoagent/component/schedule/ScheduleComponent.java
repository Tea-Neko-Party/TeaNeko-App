package org.zexnocs.teanekoagent.component.schedule;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 允许自然语言计划及提醒，持久任务仍受实时授权和来源边界约束。
 *
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.4-l
 */
@TeaNekoAgentComponent(id = "schedule", order = 70, name = "定时任务", description = "允许自然语言计划及提醒，持久任务仍受实时授权和来源边界约束。", requires = {"tool"})
@PluginOwned("teaneko.agent")
public final class ScheduleComponent extends BuiltinAgentComponent {
    /** 创建本组件独立参数模板，不启动业务或修改存储。 */
    public ScheduleComponent() {
        super("schedule", Map.ofEntries(
                Map.entry("taskTimeZone", "")), List.of(
                new Field("taskTimeZone", "任务时区", "留空时由用户明确提供日历时区。", "TEXT")));
    }
}
