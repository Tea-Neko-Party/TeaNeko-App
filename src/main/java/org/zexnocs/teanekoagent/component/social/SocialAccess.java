package org.zexnocs.teanekoagent.component.social;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * 使用既有配置查询入口复核可选社交能力，不创建配置或执行发现扫描。
 * <br>2026/11/06：新能力须显式装配，历史装配不自动获得发送或共享权限。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Service
public final class SocialAccess {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final PluginRegistry plugins;
    private final Environment environment;

    /**
     * @param configs 权威查询服务
     * @param manager 配置声明
     * @param plugins 当前插件开关
     * @param environment 本地分层记忆总开关
     */
    public SocialAccess(IConfigDataService configs, AgentScopeConfigManager manager, PluginRegistry plugins, Environment environment) {
        this.configs = configs;
        this.manager = manager;
        this.plugins = plugins;
        this.environment = environment;
    }

    /**
     * @param scope 真实聊天
     * @return 已登记且启用的配置
     */
    public Optional<AgentScopeConfigData> enabled(AgentScope scope) {
        if (!plugins.isEnabled("teaneko.agent")) { return Optional.empty(); }
        return configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .filter(AgentScopeConfigData::isEnabled);
    }

    /**
     * @param scope 真实聊天
     * @param component 独立组件标识
     * @return 显式启用的参数
     */
    public Optional<JsonNode> settings(AgentScope scope, String component) {
        return enabled(scope).map(AgentScopeConfigData::assembly)
                .filter(value -> value.parameters().containsKey(component))
                .map(value -> value.settings(component)).filter(value -> value.path("enabled").asBoolean(true));
    }

    /**
     * @param scope 真实聊天
     * @return 历史与记忆的当前读取开关
     */
    public boolean readable(AgentScope scope) {
        return environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true)
                && enabled(scope).filter(value -> value.isHistoryReadEnabled() && value.isMemoryReadEnabled()).isPresent();
    }
}
