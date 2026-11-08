package org.zexnocs.teanekoagent.runtime.delivery;

/**
 * 通用交付队列的有界限制。
 * <br>2026/11/07：无业务插件时仍保持发送顺序、容量和取消边界。
 *
 * @param timeoutSeconds 排队及交付总时限
 * @param maxQueued 单个目标的排队上限
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record AgentReplyQueueLimits(int timeoutSeconds, int maxQueued) implements IAgentReplyQueueLimits {
    /**
     * @param timeoutSeconds 排队及交付总时限
     * @param maxQueued 单个目标排队数
     * @throws IllegalArgumentException 当策略无界或不在支持范围时
     */
    public AgentReplyQueueLimits {
        if (timeoutSeconds < 1 || timeoutSeconds > 180 || maxQueued < 1 || maxQueued > 128) {
            throw new IllegalArgumentException("交付队列容量或时限无效。");
        }
    }

    /** @return 宿主缺省的有界策略 */
    public static AgentReplyQueueLimits defaults() {
        return new AgentReplyQueueLimits(90, 16);
    }
}
