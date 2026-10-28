package org.zexnocs.teanekoagent.agent.conversation;

import org.zexnocs.teanekoagent.agent.AgentHandle;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import java.util.Optional;

/**
 * 宿主会话生命周期端口；管理调用者负责权限，不能直接作为开放的模型工具。
 * 不创建记忆空间、不删除持久化数据，也不替代后续 namespace 绑定事务。
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentConversationControl {
    /**
     * 读取只含不可变数据的快照；不创建会话或延长空闲有效期。
     * @param handle 目标实例
     * @param scope 来源客户端及作用域
     * @return 有效的内存会话快照，不存在、已关闭或已空闲过期时为空
     * @throws NullPointerException 当实例或作用域为空时
     */
    Optional<EntitySnapshot> snapshot(AgentHandle handle, AgentScope scope);

    /**
     * 原子切换目标会话代次，拒绝旧结果并在锁外取消旧轮次；不等待远端副作用回滚。
     * 旧运行槽保留至结束回执，新代次请求随后顺序执行。
     * @param handle 目标实例
     * @param scope 需要重建上下文的作用域
     * @return 新代次；没有有效会话时为空且不创建空邮箱
     * @throws NullPointerException 当实例或作用域为空时
     */
    Optional<AgentConversationStamp> invalidate(AgentHandle handle, AgentScope scope);

    /**
     * 按最新本地策略惰性清理过期空闲项，并将可淘汰项收缩至容量以内。
     * @return 实际移除数量，不包含运行或等待中的会话
     * @throws IllegalStateException 当保留策略无法读取时
     */
    int purgeIdle();
}
