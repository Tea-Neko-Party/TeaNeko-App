package org.zexnocs.teanekoagent.adapter.config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Objects;

/**
 * 从真实 /cfg 记录读取新 Agent 的开关；缺失时不创建默认记录、不自动开启。
 * 私聊 scopeId 沿用宿主 UUID 规则，clientId 继续保留于身份和后续会话边界。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class ConfigDataAgentScopePolicy implements IAgentScopePolicyPort {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;

    /**
     * 保存配置端口，构造时不读取数据库或注册 scope。
     * @param configs 既有配置服务
     * @param manager 新 Agent 配置声明
     */
    public ConfigDataAgentScopePolicy(IConfigDataService configs, AgentScopeConfigManager manager) {
        this.configs = Objects.requireNonNull(configs, "配置服务不能为空。");
        this.manager = Objects.requireNonNull(manager, "Agent 配置声明不能为空。");
    }

    /**
     * 使用已有作用域键读取一次配置并转为快照；异常留给统一门控转换为不可用。
     * @param scope 宿主作用域
     * @return 真实登记策略或明确未登记状态
     */
    @Override public AgentScopePolicy read(AgentScope scope) {
        Objects.requireNonNull(scope, "作用域不能为空。");
        return configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .map(AgentScopeConfigData::toPolicy).orElseGet(AgentScopePolicy::unregistered);
    }
}
