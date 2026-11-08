package org.zexnocs.teanekoplugin.agent.automation.skill.adapter;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoplugin.agent.automation.skill.ISkillPolicy;
import org.zexnocs.teanekoplugin.agent.automation.skill.SkillPolicy;

import java.util.List;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 合并本地总开关和原作用域配置；目录路径仅取本地配置，不经聊天写入。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：业务参数由所属组件解释，实时复核装配与插件资格。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.automation")
public final class ConfiguredSkillPolicy implements ISkillPolicy {
    private final AgentComponentAccess components;
    private final Environment environment;
    /**
     * 借用原配置端口，构造不读取作用域。
     * @param components 当前有效组件参数
     * @param environment 本地 Spring 配置
     */
    public ConfiguredSkillPolicy(AgentComponentAccess components, Environment environment) {
        this.components = components;
        this.environment = environment;
    }
    /**
     * 只允许实际已启用记录选入显式 Skill，不从缺失配置推导默认授权。
     * @param scope 真实作用域
     * @return 当前策略
     */
    @Override public SkillPolicy read(AgentScope scope) {
        var config = components.settings(scope, "skill").orElse(null);
        if (config == null || !environment.getProperty("tea-neko.agent.skills.enabled", Boolean.class, true)) {
            return new SkillPolicy(false, List.of());
        }
        return SkillPolicy.parse(config.path("skillsEnabled").asBoolean(true), config.path("skills").asString(""));
    }
}
