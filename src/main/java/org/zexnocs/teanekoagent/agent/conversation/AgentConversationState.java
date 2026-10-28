package org.zexnocs.teanekoagent.agent.conversation;

import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import java.time.Instant;
import java.util.Objects;

/**
 * Conversation 实体的不可变生命周期组件，运行句柄、监听和排队容器保留在邮箱拥有者中。
 * @param stamp 当前可信会话代次
 * @param lastActivity 最近接收或完成请求的时刻；运行期间不据此淘汰
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentConversationState(AgentConversationStamp stamp, Instant lastActivity) {
    public static final ComponentKey<AgentConversationState> COMPONENT =
            ComponentKey.of("agent.conversation-state", AgentConversationState.class);

    /**
     * 固定会话生命周期数据，组件中不保留服务或可变控制。
     * @param stamp 当前代次
     * @param lastActivity 最近活动时间
     * @throws NullPointerException 当代次或时间为空时
     */
    public AgentConversationState {
        Objects.requireNonNull(stamp, "会话代次不能为空。");
        Objects.requireNonNull(lastActivity, "会话活动时间不能为空。");
    }
}
