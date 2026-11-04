package org.zexnocs.teanekoagent.component;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.plugin.AgentPluginContext;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import java.util.List;
import java.util.Set;

/**
 * 将原 Spring 组件实现接入既有 ECS 执行与插件代次边界，不重建业务 Bean。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @param pluginId 真实归属插件
 * @param componentId 序列化组件标识
 * @param component 原容器实现
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public record AgentComponentFeature(String pluginId, String componentId, IAgentComponent component) implements AgentFeature {
    /** @return 与旧能力不冲突的内部稳定 ID。 */
    @Override
    public String id() {
        return "component-" + componentId;
    }

    /** @return 组件拥有的独立写入命名空间。 */
    @Override
    public String componentPrefix() {
        return "agent.component." + componentId + ".";
    }

    /** @return 显式声明的数据键，不允许写入其他业务组件。 */
    @Override
    public Set<ComponentKey<?>> components() {
        return component.keys();
    }

    /** @return 实际业务来源依赖，仍使用原插件权限与版本边界。 */
    @Override
    public Set<String> dependencies() {
        return component.features();
    }

    /**
     * @param context 本轮真实配置与数据
     * @return 不含秘密的组件参数快照
     */
    @Override
    public ComponentPatch turnComponents(AgentPluginContext context) {
        return component.initialize(context.definition().assembly());
    }

    /** @return 由组件实现提供、在原执行器上运行的 ECS 系统。 */
    @Override
    public List<SystemContribution> systems() {
        return component.systems();
    }

    /** @return 原组件声明的工具来源，不绕过工具组件开关。 */
    @Override
    public org.zexnocs.teanekoagent.runtime.IAgentToolSource tools() {
        return component.tools();
    }

    /** @return 原组件声明的资料来源。 */
    @Override
    public org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource prompts() {
        return component.prompts();
    }

    /** @return 原组件拥有的资料名。 */
    @Override
    public Set<String> promptNames() {
        return component.promptNames();
    }
}
