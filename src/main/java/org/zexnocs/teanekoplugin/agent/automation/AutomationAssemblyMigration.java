package org.zexnocs.teanekoplugin.agent.automation;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.IAgentAssemblyMigration;
import tools.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;

/**
 * 分离通用工具中的旧命令开关，只有原显式开启才新增命令装配。
 * <br>2026/11/07：保留旧权限选择，冲突不自动覆盖。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class AutomationAssemblyMigration implements IAgentAssemblyMigration {
    /** @return 自动化配置迁移标识。 */
    @Override public String id() {
        return "agent.automation.490";
    }

    /**
     * @param document 独立装配文档
     * @throws IllegalArgumentException 当新旧命令开关冲突时
     */
    @Override
    public void migrate(ObjectNode document) {
        var components = (ObjectNode) document.path("components");
        if (!(components.get("tool") instanceof ObjectNode tool) || !tool.has("commandToolsEnabled")) {
            return;
        }
        var original = tool.get("commandToolsEnabled");
        if (!original.isBoolean()) {
            throw new IllegalArgumentException("旧命令开关必须为布尔值。");
        }
        var command = components.get("command");
        if (command != null && (!command.isObject() || command.has("commandToolsEnabled")
                && !command.get("commandToolsEnabled").equals(original))) {
            throw new IllegalArgumentException("新旧命令组件参数冲突，请明确保留值。");
        }
        if (command instanceof ObjectNode value) {
            value.set("commandToolsEnabled", original);
        } else if (original.asBoolean()) {
            components.putObject("command").set("commandToolsEnabled", original);
        }
        tool.remove("commandToolsEnabled");
    }

    /**
     * 2026/11/07：旧平铺命令开关缺省或关闭时不安装新命令组件，其他已选能力仍完整保留。
     * @param configuration 完整历史配置，只读取旧命令开关
     * @param document 已生成且未过滤插件状态的独立装配
     * @throws IllegalArgumentException 当旧命令开关不是布尔值时
     */
    @Override
    public void migrateLegacy(ObjectNode configuration, ObjectNode document, Map<String, ObjectNode> templates) {
        // 2026/11/07：历史业务字段由插件消费，关闭或缺省命令开关不新增命令能力。
        org.zexnocs.teanekoplugin.agent.support.LegacyAgentComponents.install(configuration, document, templates,
                java.util.List.of("skill", "schedule"));
        if (!configuration.has("skillsEnabled")
                && document.path("components").get("skill") instanceof ObjectNode skill) {
            skill.put("skillsEnabled", false);
        }
        var flag = configuration.get("commandToolsEnabled");
        if (flag != null && !flag.isBoolean()) {
            throw new IllegalArgumentException("旧命令开关必须为布尔值。");
        }
        if (flag == null || !flag.asBoolean()) {
            ((ObjectNode) document.path("components")).remove("command");
        } else {
            org.zexnocs.teanekoplugin.agent.support.LegacyAgentComponents.install(configuration, document, templates,
                    java.util.List.of("command"));
        }
    }

    /** @return 自动化组件的等价能力归属。 */
    @Override public Map<String, Set<String>> featureAliases() {
        return Map.of("teaneko.agent:component-skill", Set.of("agent.automation:component-skill"),
                "teaneko.agent:component-schedule", Set.of("agent.automation:component-schedule"));
    }
}
