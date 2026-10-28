package org.zexnocs.teanekoagent.agent.conversation;

/**
 * 独立的本地会话保留策略端口，读取须快速返回，不进行数据库或网络访问。
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentConversationDefaults {
    /**
     * 在邮箱锁外读取策略，新入队和显式清理使用本次快照。
     * @return 非空且已校验的保留策略
     * @throws IllegalStateException 当本地策略不可用时
     */
    AgentConversationSettings resolve();
}
