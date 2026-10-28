package org.zexnocs.teanekoagent.agent.conversation;

import java.time.Duration;
import java.util.Objects;

/**
 * 内存会话保留策略，空闲到期与长期记忆、历史落库互不替代。
 * @param maxConversations 当前服务允许保留的会话数，包含运行与排队中的会话
 * @param idleTtl 无在途请求后的空闲有效期
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentConversationSettings(int maxConversations, Duration idleTtl) {
    /**
     * 校验策略；不允许通过零容量或零时长隐式关闭保护。
     * @param maxConversations 正会话容量
     * @param idleTtl 正空闲有效期
     * @throws NullPointerException 当有效期为空时
     * @throws IllegalArgumentException 当容量或有效期不为正时
     */
    public AgentConversationSettings {
        Objects.requireNonNull(idleTtl, "会话空闲有效期不能为空。");
        if (maxConversations < 1 || idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("会话容量和空闲有效期必须为正。");
        }
    }

    /**
     * 提供不读取外部配置的默认策略。
     * @return 最多一千个会话、空闲三十分钟的策略
     */
    public static AgentConversationSettings defaults() {
        return new AgentConversationSettings(1000, Duration.ofMinutes(30));
    }
}
