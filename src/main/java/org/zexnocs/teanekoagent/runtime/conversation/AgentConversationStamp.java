package org.zexnocs.teanekoagent.runtime.conversation;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.Objects;
import java.util.UUID;

/**
 * 宿主绑定的会话代次，缓存淘汰后重建也生成不同标识，不携带运行控制或权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param key client/scope/实例边界
 * @param epoch 当前内存会话代次
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
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
