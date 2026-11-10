package org.zexnocs.teanekoplugin.agent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.plugins.IManagementResourceMigration;
import java.util.Map;

/**
 * 插件控制台贡献及原资源授权的精确迁移，不新增授权。
 * <br>2026/11/07：资源按当前插件归属显示，保留旧白名单的选择。
 * <br>2026/11/10：注册表情包全局识图模型页面与配置入口。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.1-l
 */
@Component
public final class ExpressionConsoleContribution implements IManagementResourceMigration,
        org.zexnocs.teanekoapp.web.plugins.IPluginConsoleContribution {
    /** @return 2026/11/10：插件独立的全局视觉模型配置页面。 */
    @Override public java.util.List<org.zexnocs.teanekoapp.web.plugins.PluginConsolePage> pages() {
        return java.util.List.of(new org.zexnocs.teanekoapp.web.plugins.PluginConsolePage(
                "sticker-model", "表情包识图模型", "设置全局备用模型并检测图片识别能力。",
                "agent.expression:sticker-model", 200, java.util.Set.of()));
    }
    /** @return 当前插件的稳定标识。 */
    @Override public String pluginId() {
        return "agent.expression";
    }

    /** @return 原授权键到当前资源归属的精确映射。 */
    @Override public Map<String, String> resourceMappings() {
        return Map.of(
                "teaneko.agent:stickers", "agent.expression:stickers",
                "teaneko.agent:stickers.preview", "agent.expression:stickers.preview",
                "teaneko.agent:stickers.change", "agent.expression:stickers.change",
                "teaneko.agent:stickers.operation", "agent.expression:stickers.operation");
    }
}
