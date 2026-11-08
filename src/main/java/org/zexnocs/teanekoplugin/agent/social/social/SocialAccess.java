package org.zexnocs.teanekoplugin.agent.social.social;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoplugin.agent.cognition.memory.configuration.MemoryReadAccess;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * 使用既有配置查询入口复核可选社交能力，不创建配置或执行发现扫描。
 * <br>2026/11/06：新能力须显式装配，历史装配不自动获得发送或共享权限。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/07：社交资格组合通用组件查询与认知读取规则，避免插件依赖环。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@Service
public final class SocialAccess {
    private final AgentComponentAccess components;
    private final MemoryReadAccess memory;

    /**
     * 2026/11/07：借用组件资格和记忆隐私规则，构造不读取聊天配置。
     * @param components 当前有效组件参数
     * @param memory 认知资料读取资格
     */
    public SocialAccess(AgentComponentAccess components, MemoryReadAccess memory) {
        this.components = components;
        this.memory = memory;
    }

    /**
     * @param scope 真实聊天
     * @param component 独立组件标识
     * @return 显式启用的参数
     */
    public Optional<JsonNode> settings(AgentScope scope, String component) {
        // 2026/11/07：按通用配置入口复核插件开关和组件依赖。
        return components.settings(scope, component);
    }

    /**
     * @param scope 真实聊天
     * @return 历史与记忆的当前读取开关
     */
    public boolean readable(AgentScope scope) {
        // 2026/11/07：社交能力复用认知插件的权威读取规则。
        return memory.readable(scope);
    }
}
