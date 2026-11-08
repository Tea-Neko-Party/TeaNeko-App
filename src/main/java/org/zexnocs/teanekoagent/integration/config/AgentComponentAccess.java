package org.zexnocs.teanekoagent.integration.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * 通过权威聊天配置及已发布组件目录读取当前有效参数，不发现扩展或保存查询结果。
 * <br>2026/11/07：为插件共享通用配置资格，具体业务开关由所属插件解释。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class AgentComponentAccess {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final ObjectProvider<AgentComponentRegistry> components;
    private final ObjectMapper mapper;

    /**
     * 借用配置和延迟取得的组件目录，构造期间不触发业务 Bean 创建。
     * @param configs 权威配置查询
     * @param manager Agent 配置声明
     * @param components 已发布目录提供者
     * @param mapper 完整配置树转换器
     */
    public AgentComponentAccess(IConfigDataService configs, AgentScopeConfigManager manager,
            ObjectProvider<AgentComponentRegistry> components, ObjectMapper mapper) {
        this.configs = configs;
        this.manager = manager;
        this.components = components;
        this.mapper = mapper;
    }

    /**
     * 2026/11/08：读取组件及所属插件的全局资格，适用于不关联聊天的静态目录。
     * @param component 已声明组件标识
     * @return 所属插件当前有效时为真
     */
    public boolean available(String component) {
        return components.getObject().componentEnabled(component);
    }

    /**
     * 按插件当前资格和组件依赖读取显式启用的参数，旧配置通过插件迁移声明解释。
     * @param scope 真实聊天
     * @param component 独立组件标识
     * @return 当前有效的参数，不登记或自动启用聊天
     */
    public Optional<JsonNode> settings(AgentScope scope, String component) {
        return configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .flatMap(config -> settings(config, component));
    }

    /**
     * 2026/11/07：复用调用方本次已读取的权威配置，仍重新复核插件及组件依赖。
     * @param config 当前完整配置行，不是前端投影
     * @param component 已声明组件标识
     * @return 当前有效的无秘密参数
     */
    public Optional<JsonNode> settings(AgentScopeConfigData config, String component) {
        var registry = components.getObject();
        if (!config.toPolicy().enabled() || !registry.componentEnabled(component)) {
            return Optional.empty();
        }
        var assembly = registry.effective(registry.normalizeConfiguration(mapper.writeValueAsString(config)));
        if (!assembly.parameters().containsKey(component)) {
            return Optional.empty();
        }
        var parameters = assembly.settings(component);
        return parameters.path("enabled").asBoolean(true) ? Optional.of(parameters) : Optional.empty();
    }
}
