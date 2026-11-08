package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationWindowPolicy;

/**
 * 将记忆组件的上下文窗口设置适配给通用会话底座。
 * <br>2026/11/08：未安装或停用时不保留新窗口，重新启用读取原组件选择。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class MemoryConversationWindowPolicy implements IAgentConversationWindowPolicy {
    private final AgentComponentAccess components;

    /**
     * 借用权威装配读取，不在构造时加载聊天配置。
     * @param components 当前有效组件参数
     */
    public MemoryConversationWindowPolicy(AgentComponentAccess components) {
        this.components = components;
    }

    /**
     * 记忆参数为负一时继承通用上限，组件缺失时返回零。
     * @param scope 真实聊天
     * @param configuredMessages 当前通用窗口上限
     * @return 当前记忆组件允许的窗口
     */
    @Override
    public int maxMessages(AgentScope scope, int configuredMessages) {
        return components.settings(scope, "memory").map(value -> {
            int configured = value.path("historyMaxMessages").asInt(-1);
            return configured < 0 ? configuredMessages : configured;
        }).orElse(0);
    }
}
