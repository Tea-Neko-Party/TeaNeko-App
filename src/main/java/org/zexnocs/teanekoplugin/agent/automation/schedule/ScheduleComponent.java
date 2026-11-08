package org.zexnocs.teanekoplugin.agent.automation.schedule;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 允许自然语言计划及提醒，持久任务仍受实时授权和来源边界约束。
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentComponent(id = "schedule", order = 70, name = "定时任务", description = "允许自然语言计划及提醒，持久任务仍受实时授权和来源边界约束。", requires = {"tool"})
@PluginOwned("agent.automation")
public final class ScheduleComponent extends DeclaredAgentComponent {
    /** 创建本组件独立参数模板，不启动业务或修改存储。 */
    public ScheduleComponent() {
        super("schedule", Map.ofEntries(
                Map.entry("taskTimeZone", "")), List.of(
                new Field("taskTimeZone", "任务时区", "留空时由用户明确提供日历时区。", "TEXT")));
    }

    /**
     * 2026/11/07：在声明类型校验后检查本组件业务范围，不依赖宿主字段表。
     * @param parameters 本次独立参数树
     * @throws IllegalArgumentException 当参数类型或业务范围无效时
     */
    @Override
    public void validate(tools.jackson.databind.JsonNode parameters) {
        super.validate(parameters);
        var zone = parameters.path("taskTimeZone").asString("");
        if (!zone.isEmpty()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                java.time.ZoneId.of(zone);
            } catch (java.time.DateTimeException invalid) {
                throw new IllegalArgumentException("任务时区必须为空或合法的 ZoneId，例如 Asia/Shanghai。");
            }
        }
    }
}
