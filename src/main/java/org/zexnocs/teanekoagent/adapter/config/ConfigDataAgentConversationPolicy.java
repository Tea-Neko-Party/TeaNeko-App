package org.zexnocs.teanekoagent.adapter.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentHandle;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Objects;

/**
 * 懒读本地默认值并应用已登记 scope 的窗口覆盖，不创建配置或承担读取之外的业务。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class ConfigDataAgentConversationPolicy implements IAgentConversationPolicy {
    private final Environment environment;
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;

    /**
     * 保存依赖，不在装配时读取本地属性或数据库。
     * @param environment Spring 本地配置
     * @param configs 既有配置服务
     * @param manager Agent 配置声明
     * @throws NullPointerException 当环境、配置服务或配置声明为空时
     */
    public ConfigDataAgentConversationPolicy(Environment environment, IConfigDataService configs, AgentScopeConfigManager manager) {
        this.environment = Objects.requireNonNull(environment, "会话配置环境不能为空。");
        this.configs = Objects.requireNonNull(configs, "作用域配置服务不能为空。");
        this.manager = Objects.requireNonNull(manager, "Agent 配置声明不能为空。");
    }

    /**
     * 在轮次出队后读取本轮设置，负一继承本地值；配置失败不导出原值或异常链。
     * @param handle 目标实例
     * @param request 可信原始请求
     * @return 已校验的本轮策略
     * @throws IllegalStateException 当本地值或已有 scope 覆盖无效时
     */
    @Override public AgentConversationOptions resolve(AgentHandle handle, AgentRunRequest request) {
        try {
            int messages = environment.getProperty("tea-neko.agent.conversation.max-history-messages", Integer.class, 40);
            long input = environment.getProperty("tea-neko.agent.conversation.max-input-bytes", Long.class, 262144L);
            int completed = environment.getProperty("tea-neko.agent.conversation.max-completed-requests", Integer.class, 32);
            long replay = environment.getProperty("tea-neko.agent.conversation.max-replay-bytes", Long.class, 1048576L);
            var local = new AgentConversationOptions(messages, input, completed, replay);
            var scope = configs.getConfigData(manager, AgentScopeConfigData.class, request.actor().scope().scopeId());
            if (scope.isPresent()) {
                var data = scope.orElseThrow();
                if (data.getHistoryMaxMessages() < -1 || data.getInputMaxBytes() < -1 || data.getInputMaxBytes() == 0) {
                    throw new IllegalArgumentException();
                }
                if (data.getHistoryMaxMessages() >= 0) { messages = data.getHistoryMaxMessages(); }
                if (data.getInputMaxBytes() >= 1) { input = data.getInputMaxBytes(); }
            }
            return new AgentConversationOptions(messages, input, local.maxCompletedRequests(), local.maxReplayBytes());
        } catch (RuntimeException ignored) { throw new IllegalStateException("Agent 历史窗口或重放配置不可用。"); }
    }
}
