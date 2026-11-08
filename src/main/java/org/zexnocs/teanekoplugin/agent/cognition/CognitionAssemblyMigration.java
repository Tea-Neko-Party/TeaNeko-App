package org.zexnocs.teanekoplugin.agent.cognition;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.IAgentAssemblyMigration;
import tools.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;

/**
 * 合并旧跨聊天回忆装配，参数冲突不覆盖用户原值。
 * <br>2026/11/07：保持原开启意愿与隐私边界，转换可重复执行。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class CognitionAssemblyMigration implements IAgentAssemblyMigration {
    /** @return 认知插件迁移标识。 */
    @Override public String id() {
        return "agent.cognition.490";
    }

    /**
     * @param document 本次独立的版本化装配树
     * @throws IllegalArgumentException 当旧装配缺少依赖或新旧值冲突时
     */
    @Override
    public void migrate(ObjectNode document) {
        var components = (ObjectNode) document.path("components");
        var shared = components.get("shared-memory");
        if (shared == null) {
            return;
        }
        if (!(components.get("memory") instanceof ObjectNode memory) || !shared.isObject()) {
            throw new IllegalArgumentException("旧跨聊天回忆装配缺少记忆组件，无法自动迁移。");
        }
        if (shared.size() > 1 || shared.size() == 1 && !shared.has("enabled")
                || shared.has("enabled") && !shared.path("enabled").isBoolean()) {
            throw new IllegalArgumentException("旧跨聊天回忆参数不符合原契约，无法自动迁移。");
        }
        boolean enabled = shared.path("enabled").asBoolean(true);
        if (memory.has("crossChatRecallEnabled") && (!memory.path("crossChatRecallEnabled").isBoolean()
                || memory.path("crossChatRecallEnabled").asBoolean() != enabled)) {
            throw new IllegalArgumentException("记忆与跨聊天回忆的新旧开关冲突，请明确保留值。");
        }
        memory.put("crossChatRecallEnabled", enabled);
        components.remove("shared-memory");
    }

    /**
     * 2026/11/07：仅恢复本插件历史默认组件，原始节点由所属组件校验。
     * @param configuration 完整旧配置树
     * @param document 独立目标装配
     * @param templates 当前安装目录的默认参数
     */
    @Override
    public void migrateLegacy(ObjectNode configuration, ObjectNode document, Map<String, ObjectNode> templates) {
        org.zexnocs.teanekoplugin.agent.support.LegacyAgentComponents.install(configuration, document, templates,
                java.util.List.of("memory", "personality"));
    }

    /** @return 认知能力的等价新归属。 */
    @Override
    public Map<String, Set<String>> featureAliases() {
        return Map.of(
                "teaneko.agent:component-memory", Set.of("agent.cognition:component-memory"),
                "teaneko.agent:component-shared-memory", Set.of("agent.cognition:component-memory"),
                "teaneko.agent:component-personality", Set.of("agent.cognition:component-personality"),
                "teaneko.agent:builtin-memory", Set.of("agent.cognition:builtin-memory"),
                "teaneko.agent:long-memory", Set.of("agent.cognition:long-memory"),
                "teaneko.agent:layered-memory", Set.of("agent.cognition:layered-memory"),
                "teaneko.agent:style-observations", Set.of("agent.cognition:style-observations"),
                "teaneko.agent:builtin-tools", Set.of("agent.cognition:memory-tools", "agent.automation:command-tools"));
    }
}
