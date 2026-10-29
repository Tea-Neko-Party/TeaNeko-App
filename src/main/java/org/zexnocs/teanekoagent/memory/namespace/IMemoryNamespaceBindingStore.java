package org.zexnocs.teanekoagent.memory.namespace;

import org.zexnocs.teanekoagent.agent.AgentScope;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 作用域绑定及操作回执的事务端口，与元数据存储同域协作，不依赖会话或 ConfigData 缓存。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IMemoryNamespaceBindingStore {
    /**
     * 读取权威绑定；没有显式记录时返回零版本默认共享引用，不自动注册或写入。
     * @param scope 真实 client/scope
     * @return 当前事务观察到的绑定
     */
    CompletionStage<MemoryNamespaceBinding> readBinding(AgentScope scope);

    /**
     * 同一事务读取绑定和数据代次；使用该快照的动作仍须在提交时重新验证。
     * @param scope 真实作用域
     * @return 默认共享空间尚未登记时为空；显式绑定目标损坏或缺失则异常结束
     */
    CompletionStage<Optional<MemoryNamespaceReference>> readReference(AgentScope scope);

    /**
     * 原子执行创建/配额/绑定/回执，或绑定已有目标；本方法不完成聊天权限和内存失效。
     * scope 加 requestId 定位原始操作，同参数重复返回已保存回执，不再次执行或重新绑定。
     * 相同定位但参数不同只返回冲突，不覆盖原命令及原回执。
     * @param request 上层认证授权后固定的原始命令
     * @param maxCreated 本次个人配额，仅创建模式使用
     * @param now 原始操作时刻，不参与幂等命令比较
     * @return 完整事务提交后的结果，拒绝结果也保留以防重试变成新动作
     * @throws IllegalArgumentException 当配额不是正整数时
     */
    CompletionStage<MemoryNamespaceBindingOutcome> apply(MemoryNamespaceBindingRequest request, int maxCreated, Instant now);
}
