package org.zexnocs.teanekoagent.component.skill.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.skill.ISkillPolicy;
import org.zexnocs.teanekoagent.component.skill.SkillPolicy;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.List;

/**
 * 合并本地总开关和原作用域配置；目录路径仅取本地配置，不经聊天写入。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@Service
public final class ConfiguredSkillPolicy implements ISkillPolicy {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final Environment environment;
    /**
     * 借用原配置端口，构造不读取作用域。
     * @param configs 配置读取服务
     * @param manager 原 Agent 声明
     * @param environment 本地 Spring 配置
     */
    public ConfiguredSkillPolicy(IConfigDataService configs, AgentScopeConfigManager manager, Environment environment) {
        this.configs = configs; this.manager = manager; this.environment = environment;
    }
    /**
     * 只允许实际已启用记录选入显式 Skill，不从缺失配置推导默认授权。
     * @param scope 真实作用域
     * @return 当前策略
     */
    @Override public SkillPolicy read(AgentScope scope) {
        var config = configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId()).orElse(null);
        if (config == null || !config.isEnabled() || !environment.getProperty("tea-neko.agent.skills.enabled", Boolean.class, true)) {
            return new SkillPolicy(false, List.of());
        }
        return SkillPolicy.parse(config.isSkillsEnabled(), config.getSkills());
    }
}
