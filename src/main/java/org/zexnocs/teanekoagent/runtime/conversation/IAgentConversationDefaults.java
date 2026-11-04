package org.zexnocs.teanekoagent.runtime.conversation;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 独立的本地会话保留策略端口，读取须快速返回，不进行数据库或网络访问。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
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
