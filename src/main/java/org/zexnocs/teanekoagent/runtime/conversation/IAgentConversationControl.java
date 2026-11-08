package org.zexnocs.teanekoagent.runtime.conversation;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 宿主会话生命周期端口；管理调用者负责权限，不能直接作为开放的模型工具。
 * 引用同步可按需登记缺失的默认共享空间；不删除持久化数据，也不替代 namespace 绑定事务。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public interface IAgentConversationControl {
    /**
     * 管理提交后复核实际人格，只有身份变化才换代；重放不重复清除新窗口。
     * @param scope 真实操作作用域
     * @param correctionKey 已由业务层验证的补正 key
     * @param shared 是否协调共享该 key 的全部已知作用域，否则仅当前 scope
     * @param parent 借用管理控制
     * @return 实际换代的会话数
     * @throws UnsupportedOperationException 当宿主未支持人格协调时
     */
    default CompletionStage<Integer> synchronizePromptContext(AgentScope scope, String correctionKey, boolean shared, ExecutionControl parent) {
        throw new UnsupportedOperationException("当前会话端口不支持人格管理同步。");
    }
    /**
     * 管理清空提交后同步引用同一空间的全部已知作用域，不将共享数据误当成单群私有数据。
     * @param namespace 已由业务边界验证的空间键，会话层不解析管理格式
     * @param parent 借用管理控制
     * @return 实际推进代次的会话数
     * @throws UnsupportedOperationException 当实现不支持跨作用域同步时
     */
    default CompletionStage<Integer> synchronizePartition(String namespace, ExecutionControl parent) {
        throw new UnsupportedOperationException("当前会话端口不支持共享空间同步。");
    }
    /**
     * 持久化绑定提交后读取当前引用并协调同 scope 的所有实例；不以旧操作回执恢复状态。
     * 调用方负责管理权限，并保持借用父控制存活至完成。
     * @param scope 真实作用域
     * @param parent 借用管理控制
     * @return 实际更换 epoch 的会话数
     * @throws UnsupportedOperationException 当实现尚未支持持久化引用同步时
     */
    default CompletionStage<Integer> synchronizeContext(AgentScope scope, ExecutionControl parent) {
        throw new UnsupportedOperationException("会话端口尚未支持持久化记忆引用同步。");
    }
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
