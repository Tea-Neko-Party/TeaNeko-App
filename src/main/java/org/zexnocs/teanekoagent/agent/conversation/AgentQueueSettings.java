package org.zexnocs.teanekoagent.agent.conversation;

import java.time.Duration;
import java.util.Objects;

/**
 * 单次入队的不可变策略，不包含运行控制或业务权限。
 * @param maxWaiting 最多等待数量，不包含已占运行槽的请求；零表示不排队
 * @param waitTimeout 等待上限，出队后停止该计时
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentQueueSettings(int maxWaiting, Duration waitTimeout) {
    /**
     * 拒绝负数容量及非正时限。
     * @param maxWaiting 等待容量
     * @param waitTimeout 等待时限
     * @throws IllegalArgumentException 当容量为负或时限非正时
     * @throws NullPointerException 当等待时限为空时
     */
    public AgentQueueSettings {
        Objects.requireNonNull(waitTimeout, "排队时限不能为空。");
        if (maxWaiting < 0 || waitTimeout.isZero() || waitTimeout.isNegative()) {
            throw new IllegalArgumentException("排队容量不能为负，等待时限必须为正。");
        }
    }

    /**
     * 提供计划默认的五条等待和三十秒时限。
     * @return 默认排队策略
     */
    public static AgentQueueSettings defaults() { return new AgentQueueSettings(5, Duration.ofSeconds(30)); }
}
