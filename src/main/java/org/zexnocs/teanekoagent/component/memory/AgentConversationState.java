package org.zexnocs.teanekoagent.component.memory;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

// 2026/11/04：组件数据与系统按职责统一归类，持久标识保持不变。
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationStamp;

import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import java.time.Instant;
import java.util.Objects;

/**
 * Conversation 实体的不可变生命周期组件，运行句柄、监听和排队容器保留在邮箱拥有者中。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @param stamp 当前可信会话代次
 * @param lastActivity 最近接收或完成请求的时刻；运行期间不据此淘汰
 * 修改记录：<br>
 * 2026/11/04：组件或系统迁入统一的 component 分类，保持原执行语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
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
