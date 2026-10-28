package org.zexnocs.teanekoagent.agent.conversation;

import java.util.Objects;
import java.util.UUID;

/**
 * 宿主绑定的会话代次，缓存淘汰后重建也生成不同标识，不携带运行控制或权限。
 * @param key client/scope/实例边界
 * @param epoch 当前内存会话代次
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentConversationStamp(AgentConversationKey key, UUID epoch) {
    /**
     * 固定可信绑定，不从模型字段推断会话或代次。
     * @param key 共享会话键
     * @param epoch 宿主生成的代次标识
     * @throws NullPointerException 当必需字段为空时
     */
    public AgentConversationStamp {
        Objects.requireNonNull(key, "会话键不能为空。");
        Objects.requireNonNull(epoch, "会话代次不能为空。");
    }
}
