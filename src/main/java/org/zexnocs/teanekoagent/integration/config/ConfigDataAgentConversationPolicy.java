package org.zexnocs.teanekoagent.integration.config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationOptions;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationPolicy;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationWindowPolicy;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Objects;
import java.util.Optional;

/**
 * 懒读本地默认值并应用已登记 scope 的窗口覆盖，不创建配置或承担读取之外的业务。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * <br>2026/11/08：窗口策略交给类型化提供者，宿主不读取插件字段或固定标识。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
public final class ConfigDataAgentConversationPolicy implements IAgentConversationPolicy {
    private final Optional<IAgentConversationWindowPolicy> windows;
    private final Environment environment;
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;

    /**
     * 保存依赖，不在装配时读取本地属性或数据库。
     * @param environment Spring 本地配置
     * @param configs 既有配置服务
     * @param manager Agent 配置声明
     * @param windows 可选上下文窗口策略，多个实现由容器拒绝歧义
     * @throws NullPointerException 当环境、配置服务或配置声明为空时
     */
    public ConfigDataAgentConversationPolicy(Environment environment, IConfigDataService configs, AgentScopeConfigManager manager,
                                             Optional<IAgentConversationWindowPolicy> windows) {
        this.windows = windows;
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
            int configuredMessages = messages;
            int activeMessages = windows.map(policy -> policy.maxMessages(request.actor().scope(), configuredMessages))
                    .orElse(0);
            return new AgentConversationOptions(activeMessages, input, local.maxCompletedRequests(), local.maxReplayBytes());
        } catch (RuntimeException failure) { throw new org.zexnocs.teanekocore.logger.DiagnosticException("Agent 历史窗口或重放配置不可用。", failure); }
    }
}
