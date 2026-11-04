package org.zexnocs.teanekoagent.integration.config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.access.IAgentScopePolicyPort;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Objects;

/**
 * 从真实 /cfg 记录读取新 Agent 的开关；缺失时不创建默认记录、不自动开启。
 * 私聊 scopeId 沿用宿主 UUID 规则，clientId 继续保留于身份和后续会话边界。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：动态准入验证装配并解析聊天私有模型路由。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@Service
public final class ConfigDataAgentScopePolicy implements IAgentScopePolicyPort {
    private org.zexnocs.teanekoagent.component.@org.jspecify.annotations.Nullable AgentComponentRegistry components;

    /**
     * 2026/11/04：动态准入校验组件仍实际注册及启用，旧独立宿主可不装配目录。
     * @param components 当前组件目录
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installComponents(org.zexnocs.teanekoagent.component.AgentComponentRegistry components) {
        this.components = components;
    }
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
                .map(config -> {
                    // 2026/11/04：真实副作用的既有门控同时复核本轮组件参数和归属。
                    var assembly = components != null && config.getComponents() != null
                            ? components.validate(config.getComponents()) : config.assembly();
                    var policy = config.toPolicy();
                    String route = !assembly.legacy() && assembly.settings("llm").path("source").asString().equals("custom")
                            ? org.zexnocs.teanekoagent.component.llm.ScopedLLMRouteSource.route(scope.scopeId(), assembly)
                            : policy.modelRoute();
                    return new AgentScopePolicy(policy.registered(), policy.enabled(), policy.mode(), route, assembly);
                }).orElseGet(AgentScopePolicy::unregistered);
    }
}
