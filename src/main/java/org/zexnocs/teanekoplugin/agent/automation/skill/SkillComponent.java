package org.zexnocs.teanekoplugin.agent.automation.skill;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 将允许加载的技能作为独立组件参数，不把技能说明当作额外工具授权。
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentComponent(id = "skill", order = 60, name = "技能", description = "按需加载显式选择的本地技能说明。", requires = "tool")
@PluginOwned("agent.automation")
public final class SkillComponent extends DeclaredAgentComponent {
    private org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoplugin.agent.automation.skill.adapter.AgentSkills> runtime;

    /**
     * 2026/11/07：只保存懒端口，静态组件发现不创建技能目录或形成构造环。
     * @param runtime 已由容器管理的技能服务
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installRuntime(org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoplugin.agent.automation.skill.adapter.AgentSkills> runtime) {
        this.runtime = runtime;
    }

    /** @return 2026/11/07：技能拥有的资料声明。 */
    @Override
    public java.util.Set<String> promptNames() {
        return java.util.Set.of("agent_skills");
    }

    /** @return 2026/11/07：完整保留原技能资料的读取与重放复核。 */
    @Override
    public org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource prompts() {
        return new org.zexnocs.teanekoagent.runtime.prompt.DeferredAgentPromptSource(() ->
                runtime.getObject().prompts(org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty()));
    }

    /** @return 2026/11/07：原技能选择与最终工具复核。 */
    @Override
    public org.zexnocs.teanekoagent.runtime.IAgentToolSource tools() {
        return new org.zexnocs.teanekoagent.runtime.DeferredAgentToolSource(() ->
                runtime.getObject().tools((definition, request, snapshot) -> java.util.List.of()));
    }

    /** 创建空技能选择，安装本身不会执行任何技能。 */
    public SkillComponent() {
        super("skill", Map.of("skillsEnabled", true, "skills", ""), List.of(
                new Field("skillsEnabled", "允许加载", "关闭后不读取技能资料。", "BOOLEAN"),
                new Field("skills", "技能名称", "英文逗号分隔，最多八项。", "TEXT")));
    }

    /**
     * 2026/11/07：在声明类型校验后检查本组件业务范围，不依赖宿主字段表。
     * @param parameters 本次独立参数树
     * @throws IllegalArgumentException 当参数类型或业务范围无效时
     */
    @Override
    public void validate(tools.jackson.databind.JsonNode parameters) {
        super.validate(parameters);
        SkillPolicy.parse(true, parameters.path("skills").asString(""));
    }
}
