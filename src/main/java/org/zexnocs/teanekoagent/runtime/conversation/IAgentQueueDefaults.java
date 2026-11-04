package org.zexnocs.teanekoagent.runtime.conversation;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 本地排队策略快照端口，只读取快速内存配置；不得执行阻塞 I/O 或等待业务。
 * 在邮箱锁外调用，配置错误只影响本次入队，修复后下一次重新读取。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IAgentQueueDefaults {
    /**
     * 获取本次入队的等待数量和时限，不读取模型配置或凭据。
     * @return 不可变排队策略
     */
    AgentQueueSettings resolve();
}
