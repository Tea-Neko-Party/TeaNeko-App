package org.zexnocs.teanekoagent.memory.namespace;

import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * 事务已结束后的空间元数据回执；拒绝、版本冲突与提交成功分别表达。
 * @param status 事务业务结果
 * @param namespace 已提交或冲突时观察到的空间；不存在及配额拒绝时为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryNamespaceMutation(Status status, @Nullable MemoryNamespace namespace) {
    /**
     * 校验回执结构，不能把缺失元数据的写入描述成成功。
     * @param status 结果状态
     * @param namespace 当前元数据
     * @throws NullPointerException 当状态为空时
     * @throws IllegalArgumentException 当状态与元数据存在性不一致时
     */
    public MemoryNamespaceMutation {
        Objects.requireNonNull(status, "记忆空间变更状态不能为空。");
        if ((namespace == null) != (status == Status.NOT_FOUND || status == Status.QUOTA_EXCEEDED)) {
            throw new IllegalArgumentException("记忆空间变更回执与元数据不一致。");
        }
    }

    /**
     * 不把重复确认、业务拒绝或乐观锁冲突当作新的外部动作。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status { CREATED, UNCHANGED, GENERATION_ADVANCED, NOT_FOUND, CONFLICT, QUOTA_EXCEEDED }
}
