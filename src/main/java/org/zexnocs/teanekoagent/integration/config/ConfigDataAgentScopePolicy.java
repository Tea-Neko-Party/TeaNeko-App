package org.zexnocs.teanekoagent.integration.config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.component.llm.ScopedLLMRouteSource;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.access.IAgentScopePolicyPort;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 从真实 /cfg 记录读取新 Agent 的开关；缺失时不创建默认记录、不自动开启。
 * 私聊 scopeId 沿用宿主 UUID 规则，clientId 继续保留于身份和后续会话边界。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：动态准入验证装配并解析聊天私有模型路由。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/08：完整持久配置经过声明式迁移和实时资格过滤，不以页面投影生成运行装配。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
public final class ConfigDataAgentScopePolicy implements IAgentScopePolicyPort {
    private final ObjectProvider<AgentComponentRegistry> components;
    private final ObjectMapper mapper;
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;

    /**
     * 保存配置端口，构造时不读取数据库或注册 scope。
     * @param configs 既有配置服务
     * @param manager 新 Agent 配置声明
     * @param components 已发布目录的延迟入口，构造不发现业务组件
     * @param mapper 完整配置树转换器
     */
    public ConfigDataAgentScopePolicy(IConfigDataService configs, AgentScopeConfigManager manager,
            ObjectProvider<AgentComponentRegistry> components, ObjectMapper mapper) {
        this.configs = Objects.requireNonNull(configs, "配置服务不能为空。");
        this.manager = Objects.requireNonNull(manager, "Agent 配置声明不能为空。");
        this.components = Objects.requireNonNull(components, "组件目录入口不能为空。");
        this.mapper = Objects.requireNonNull(mapper, "配置转换器不能为空。");
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
                    // 2026/11/08：完整原文用于迁移及校验，页面隐藏不会丢失运行资格或受保护参数。
                    var registry = components.getObject();
                    var assembly = registry.effective(registry.normalizeConfiguration(mapper.writeValueAsString(config)));
                    var policy = config.toPolicy();
                    // 2026/11/07：认证路由绑定持久原文摘要；运行装配另用有效组件摘要识别插件变化。
                    String route = !assembly.legacy() && assembly.settings("llm").path("source").asString().equals("custom")
                            ? ScopedLLMRouteSource.route(scope.scopeId(), config.assembly())
                            : policy.modelRoute();
                    return new AgentScopePolicy(policy.registered(), policy.enabled(), policy.mode(), route, assembly);
                }).orElseGet(AgentScopePolicy::unregistered);
    }
}
