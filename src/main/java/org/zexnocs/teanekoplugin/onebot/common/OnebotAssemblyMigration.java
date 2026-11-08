package org.zexnocs.teanekoplugin.onebot.common;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.IAgentAssemblyMigration;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Set;

/**
 * 为平台业务工具保留原能力选择，归属迁移不扩大客户端或工具授权。
 * <br>2026/11/07：旧平台能力标识转换到当前业务插件。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class OnebotAssemblyMigration implements IAgentAssemblyMigration {
    /** @return 全局唯一的兼容迁移标识。 */
    @Override
    public String id() {
        return "onebot.common.490";
    }

    /**
     * 平台组件参数结构保持兼容，无需修改装配节点。
     * @param document 独立装配文档
     */
    @Override
    public void migrate(ObjectNode document) {
    }

    /** @return 原平台能力的等价新归属。 */
    @Override
    public Map<String, Set<String>> featureAliases() {
        return Map.of("teaneko.agent:onebot", Set.of("onebot.common:onebot"));
    }
}
