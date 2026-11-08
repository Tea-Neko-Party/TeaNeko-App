package org.zexnocs.teanekoagent.runtime.conversation;

import org.zexnocs.teanekoagent.runtime.AgentScope;

/**
 * 允许上下文提供者决定当前聊天保留的会话窗口，宿主不解释业务参数。
 * <br>2026/11/08：独立于持久历史及具体组件标识，只返回通用运行限制。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface IAgentConversationWindowPolicy {
    /**
     * 按当前有效装配选择窗口上限。
     * @param scope 真实聊天
     * @param configuredMessages 已校验的系统及聊天通用上限
     * @return 非负消息上限，零表示不保留会话窗口
     */
    int maxMessages(AgentScope scope, int configuredMessages);
}
