package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * 统一复核记忆及跨聊天资料的读取资格，社交能力可复用同一隐私边界。
 * <br>2026/11/07：认知读取规则独立于社交插件，停用或撤回权限后立即失效。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class MemoryReadAccess {
    private final AgentComponentAccess components;
    private final Environment environment;

    /**
     * 借用通用配置资格和本地记忆开关。
     * @param components 已启用组件参数
     * @param environment 本地分层记忆总开关
     */
    public MemoryReadAccess(AgentComponentAccess components, Environment environment) {
        this.components = components;
        this.environment = environment;
    }

    /**
     * 读取经过兼容迁移及当前插件资格筛选的记忆参数。
     * @param scope 真实聊天
     * @return 显式启用的记忆参数
     */
    public Optional<JsonNode> settings(AgentScope scope) {
        return components.settings(scope, "memory");
    }

    /**
     * 复核本地记忆总开关及聊天的历史、记忆读取授权。
     * @param scope 真实聊天
     * @return 当前允许读取记忆资料时为真
     */
    public boolean readable(AgentScope scope) {
        return environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true)
                && settings(scope).filter(value -> value.path("historyReadEnabled").asBoolean(true)
                && value.path("memoryReadEnabled").asBoolean(true)).isPresent();
    }
}
