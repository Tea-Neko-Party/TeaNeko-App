package org.zexnocs.teanekoplugin.agent.support;

import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * 为插件自己的旧组件选择复制原始节点，不替宿主决定业务默认能力。
 * <br>2026/11/07：仅转换明确列出的已安装组件，保留布尔关闭、数值和文本原类型。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class LegacyAgentComponents {
    /** 禁止创建无状态转换器。 */
    private LegacyAgentComponents() {
    }

    /**
     * 从完整旧树恢复本插件原来默认安装的组件，未安装的实现不生成占位组件。
     * @param configuration 原历史树，只读使用
     * @param document 独立目标装配
     * @param templates 当前安装目录的公开参数模板
     * @param ids 本插件曾默认安装的稳定组件标识
     */
    public static void install(ObjectNode configuration, ObjectNode document,
                               Map<String, ObjectNode> templates, List<String> ids) {
        var installed = (ObjectNode) document.path("components");
        for (var id : ids) {
            var template = templates.get(id);
            if (template == null) {
                continue;
            }
            var parameters = template.deepCopy();
            for (var field : template.propertyNames()) {
                if (configuration.has(field)) {
                    parameters.set(field, configuration.get(field).deepCopy());
                }
            }
            installed.set(id, parameters);
        }
    }
}
