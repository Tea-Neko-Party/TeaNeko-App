package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：通过中立生命周期端口接入宿主，认知类型和业务校验仍归插件。
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.AgentMemoryReferenceException;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;
import java.util.Objects;

/**
 * 一次事务观察到的只读空间引用，供后续运行快照固定并在动作前复核，不代表永久授权。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param scope 真实作用域
 * @param namespaceId 业务空间 ID
 * @param bindingRevision 作用域引用版本
 * @param generation 空间数据代次，与绑定版本分开变化
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record MemoryNamespaceReference(AgentScope scope, MemoryNamespaceId namespaceId, long bindingRevision, long generation) implements IAgentContextReference {
    /**
     * 固定版本边界，不保存操作权限、执行句柄或存储实体。
     * @param scope 真实作用域
     * @param namespaceId 业务空间
     * @param bindingRevision 引用版本
     * @param generation 数据代次
     * @throws NullPointerException 当作用域或空间为空时
     * @throws IllegalArgumentException 当版本或代次为负时
     */
    public MemoryNamespaceReference {
        Objects.requireNonNull(scope, "引用作用域不能为空。");
        Objects.requireNonNull(namespaceId, "引用空间不能为空。");
        if (bindingRevision < 0 || generation < 0) {
            throw new IllegalArgumentException("记忆空间引用版本与代次不能为负。");
        }
    }

    /** @return 供宿主关联已知会话的空间标识，不授予空间访问权 */
    @Override
    public String partitionId() {
        return namespaceId.value();
    }

    /**
     * 按原绑定版本及空间数据代次仲裁迟到观察，不进行外部读取。
     * @param previous 原已发布引用
     * @throws AgentMemoryReferenceException 当观察迟到或同版本定位冲突时
     */
    @Override
    public void validateSuccessor(IAgentContextReference previous) {
        if (!(previous instanceof MemoryNamespaceReference memory)) {
            throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.UNAVAILABLE);
        }
        if (bindingRevision < memory.bindingRevision()
                || namespaceId.equals(memory.namespaceId()) && generation < memory.generation()) {
            throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED);
        }
        if (bindingRevision == memory.bindingRevision() && !namespaceId.equals(memory.namespaceId())) {
            throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.UNAVAILABLE);
        }
    }

}
