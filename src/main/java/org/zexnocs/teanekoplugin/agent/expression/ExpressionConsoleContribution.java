package org.zexnocs.teanekoplugin.agent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.plugins.IManagementResourceMigration;
import java.util.Map;

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
public final class ExpressionConsoleContribution implements IManagementResourceMigration {
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
