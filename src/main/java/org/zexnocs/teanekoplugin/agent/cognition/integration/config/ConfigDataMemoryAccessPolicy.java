package org.zexnocs.teanekoplugin.agent.cognition.integration.config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAccessPolicy;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。
// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。


/**
 * 从同一份真实 Agent 登记读取记忆开关，不为缺失记录构造默认启用配置。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：业务参数由所属组件解释，实时复核装配与插件资格。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class ConfigDataMemoryAccessPolicy implements IMemoryAccessPolicy {
    private final AgentComponentAccess components;

    /**
     * 保存原配置服务及声明，构造不读取数据。
     * @param components 当前有效组件参数
     */
    public ConfigDataMemoryAccessPolicy(AgentComponentAccess components) {
        this.components = components;
    }

    /**
     * 只读取当前真实登记，非法模式同样拒绝；注销不保留此前允许结果。
     * @param scope 可信来源作用域
     * @return 当前记忆策略，未登记时全部关闭
     * @throws IllegalArgumentException 当现有模式非法时
     */
    @Override public MemoryAccessPolicy read(AgentScope scope) {
        return components.settings(scope, "memory")
                .map(value -> new MemoryAccessPolicy(true, value.path("memoryReadEnabled").asBoolean(true),
                        value.path("memoryAddEnabled").asBoolean(true),
                        value.path("memoryLearningEnabled").asBoolean(true)))
                .orElseGet(() -> new MemoryAccessPolicy(false, false, false, false));
    }
}
