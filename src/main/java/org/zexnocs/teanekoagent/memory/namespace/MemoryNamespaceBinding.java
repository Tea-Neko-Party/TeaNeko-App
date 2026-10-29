package org.zexnocs.teanekoagent.memory.namespace;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentScope;
import java.time.Instant;
import java.util.Objects;

/**
 * 当前 client/scope 的权威空间引用，不以用户或 Agent 实例再次隔离共享空间。
 * @param scope 真实 client/scope
 * @param namespaceId 当前空间 ID
 * @param revision 单调递增的引用版本，隐式默认引用为零
 * @param updatedAt 最近实际引用变更时刻
 * @param lastChange 最近实际变更命令，隐式默认引用为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryNamespaceBinding(AgentScope scope, MemoryNamespaceId namespaceId, long revision,
        Instant updatedAt, @Nullable MemoryNamespaceBindingRequest lastChange) {
    /**
     * 校验引用版本与最近变更，不允许给缺失引用伪造用户来源。
     * @param scope 真实作用域
     * @param namespaceId 当前空间
     * @param revision 引用版本
     * @param updatedAt 变更时刻
     * @param lastChange 可选最近变更
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当默认引用或变更定位不一致时
     */
    public MemoryNamespaceBinding {
        Objects.requireNonNull(scope, "绑定作用域不能为空。"); Objects.requireNonNull(namespaceId, "绑定空间不能为空。");
        Objects.requireNonNull(updatedAt, "绑定变更时刻不能为空。");
        if (revision < 0 || (revision == 0) != (lastChange == null)
                || revision == 0 && (!namespaceId.equals(MemoryNamespaceId.SHARED) || !updatedAt.equals(Instant.EPOCH))
                || lastChange != null && (!lastChange.actor().scope().equals(scope) || !lastChange.target().equals(namespaceId)
                    || lastChange.expectedRevision() != revision - 1)) {
            throw new IllegalArgumentException("记忆空间绑定版本或变更定位不一致。");
        }
    }

    /**
     * 表达尚未显式修改的默认共享引用，不创建数据库行或注册 scope。
     * @param scope 真实作用域
     * @return 零版本的默认引用
     * @throws NullPointerException 当作用域为空时
     */
    public static MemoryNamespaceBinding initial(AgentScope scope) {
        return new MemoryNamespaceBinding(scope, MemoryNamespaceId.SHARED, 0, Instant.EPOCH, null);
    }

    /**
     * 为实际目标变化生成下一版本；同目标绑定由存储层返回不变，不调用本方法。
     * @param request 经校验的原始绑定命令
     * @param now 变更时刻
     * @return 下一版本引用
     * @throws IllegalStateException 当引用版本已耗尽时
     * @throws IllegalArgumentException 当命令与当前作用域或版本不一致时
     * @throws NullPointerException 当命令或时刻为空时
     */
    public MemoryNamespaceBinding next(MemoryNamespaceBindingRequest request, Instant now) {
        Objects.requireNonNull(request, "绑定变更命令不能为空。"); Objects.requireNonNull(now, "绑定变更时刻不能为空。");
        try { return new MemoryNamespaceBinding(scope, request.target(), Math.incrementExact(revision), now, request); }
        catch (ArithmeticException overflow) { throw new IllegalStateException("记忆空间绑定版本已耗尽。"); }
    }
}
