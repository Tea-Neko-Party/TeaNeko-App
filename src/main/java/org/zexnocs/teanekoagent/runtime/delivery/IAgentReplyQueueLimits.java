package org.zexnocs.teanekoagent.runtime.delivery;

/**
 * 通用交付队列的容量和等待界限，业务分段策略不进入此契约。
 * <br>2026/11/07：队列可独立于表达插件装配。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentReplyQueueLimits {
    /** @return 排队及交付总时限，单位秒 */
    int timeoutSeconds();

    /** @return 单个目标的最大排队数 */
    int maxQueued();
}
