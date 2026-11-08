package org.zexnocs.teanekoagent.runtime.delivery;

/**
 * 可选交付队列策略，缺失时宿主使用有界默认值。
 * <br>2026/11/07：业务插件负责动态配置及自身启停判断。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentReplyQueuePolicy {
    /** @return 本次入队使用的不可变容量限制 */
    IAgentReplyQueueLimits limits();
}
