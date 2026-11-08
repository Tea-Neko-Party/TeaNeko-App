package org.zexnocs.teanekoplugin.agent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.IAgentAssemblyMigration;
import tools.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;

/**
 * 保留原能力选择的语义，将旧归属转换为当前插件标识。
 * <br>2026/11/07：仅迁移标识，不自动安装组件或扩大权限。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class ExpressionAssemblyMigration implements IAgentAssemblyMigration {
    /** @return 全局唯一迁移标识。 */
    @Override public String id() {
        return "agent.expression.490";
    }

    /**
     * 此插件的组件参数结构保持兼容。
     * @param document 独立装配文档
     */
    @Override public void migrate(ObjectNode document) {}

    /**
     * 2026/11/07：仅恢复本插件历史默认组件，原始节点由所属组件校验。
     * @param configuration 完整旧配置树
     * @param document 独立目标装配
     * @param templates 当前安装目录的默认参数
     */
    @Override
    public void migrateLegacy(ObjectNode configuration, ObjectNode document, Map<String, ObjectNode> templates) {
        org.zexnocs.teanekoplugin.agent.support.LegacyAgentComponents.install(configuration, document, templates,
                java.util.List.of("expression", "sticker"));
    }

    /** @return 旧能力对应的新插件归属。 */
    @Override public Map<String, Set<String>> featureAliases() {
        return Map.of(
                "teaneko.agent:sticker-collection", Set.of("agent.expression:sticker-collection"),
                "teaneko.agent:component-expression", Set.of("agent.expression:component-expression"),
                "teaneko.agent:component-sticker", Set.of("agent.expression:component-sticker"),
                "teaneko.agent:expression", Set.of("agent.expression:expression"),
                "teaneko.agent:media-replies", Set.of("agent.expression:media-replies"));
    }
}
