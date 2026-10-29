package org.zexnocs.teanekoagent.memory.namespace;

import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * 事务结束后的绑定操作回执；保存的历史回执只说明当次结果，不证明引用仍有效或已同步内存会话。
 * @param status 当次业务状态
 * @param binding 当次观察或提交的绑定
 * @param reference 成功时的空间/版本/代次观察，业务拒绝时为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryNamespaceBindingOutcome(Status status, MemoryNamespaceBinding binding, @Nullable MemoryNamespaceReference reference) {
    /**
     * 成功必须有定位一致的引用，不把版本冲突包装成成功绑定。
     * @param status 业务状态
     * @param binding 绑定观察
     * @param reference 成功引用或空
     * @throws NullPointerException 当状态或绑定为空时
     * @throws IllegalArgumentException 当成功状态、绑定及引用不一致时
     */
    public MemoryNamespaceBindingOutcome {
        Objects.requireNonNull(status, "绑定结果状态不能为空。"); Objects.requireNonNull(binding, "绑定结果不能为空。");
        boolean successful = status == Status.BOUND || status == Status.UNCHANGED;
        if (successful != (reference != null) || reference != null && (!reference.scope().equals(binding.scope())
                || !reference.namespaceId().equals(binding.namespaceId()) || reference.bindingRevision() != binding.revision())) {
            throw new IllegalArgumentException("绑定操作结果与引用定位不一致。");
        }
    }

    /**
     * 提交成功、同目标不变与各类业务拒绝；基础设施异常仍由阶段异常传播。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status { BOUND, UNCHANGED, CONFLICT, TARGET_NOT_FOUND, QUOTA_EXCEEDED }
}
