package org.zexnocs.teanekoplugin.agent.cognition;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.plugins.IManagementResourceMigration;
import org.zexnocs.teanekoapp.web.plugins.IPluginConsoleContribution;
import org.zexnocs.teanekoapp.web.plugins.PluginConsolePage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件控制台贡献及原资源授权的精确迁移，不新增授权。
 * <br>2026/11/07：资源按当前插件归属显示，保留旧白名单的选择。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class CognitionConsoleContribution implements IManagementResourceMigration, IPluginConsoleContribution {
    /** @return 当前插件的稳定标识。 */
    @Override public String pluginId() {
        return "agent.cognition";
    }

    /** @return 原授权键到当前资源归属的精确映射。 */
    @Override public Map<String, String> resourceMappings() {
        return Map.of(
                "teaneko.agent:memory", "agent.cognition:memory",
                "teaneko.agent:profiles", "agent.cognition:profiles",
                "teaneko.agent:personality", "agent.cognition:personality",
                "teaneko.agent:layers", "agent.cognition:layers",
                "teaneko.agent:memory-space", "agent.cognition:memory-space",
                "teaneko.agent:personality-space", "agent.cognition:personality-space",
                "teaneko.agent:profile-space", "agent.cognition:profile-space",
                "teaneko.agent:layers-space", "agent.cognition:layers-space");
    }

    /** @return 随应用构建的认知管理工作区。 */
    @Override public List<PluginConsolePage> pages() {
        return List.of(new PluginConsolePage("workspace", "记忆与人格", "查看和管理记忆空间、画像及人格补正。",
                "agent.cognition:workspace", 100, Set.of("teaneko.agent:library")));
    }
}
