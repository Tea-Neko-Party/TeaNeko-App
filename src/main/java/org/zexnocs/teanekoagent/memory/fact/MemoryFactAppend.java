package org.zexnocs.teanekoagent.memory.fact;

import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;

import java.util.Objects;
import java.util.UUID;

/**
 * 一次稳定的追加意图，空间和来源由可信宿主固定，不授予写入或 DEBUG 管理权限。
 * @param operationId 单次事实动作的幂等 ID，重试不可重新生成
 * @param reference 固定空间及数据代次
 * @param subjectId 事实主体的 TeaNeko UUID
 * @param value 事实内容
 * @param source 来源和真实发起者
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryFactAppend(UUID operationId, MemoryNamespaceReference reference, UUID subjectId,
        MemoryFactValue value, MemoryFactSource source) {
    /**
     * 校验来源与固定引用属于同一真实 scope，允许主语与发起者分开保留。
     * @param operationId 稳定追加动作
     * @param reference 固定引用
     * @param subjectId 事实主语
     * @param value 内容
     * @param source 来源
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当来源作用域与引用不一致时
     */
    public MemoryFactAppend {
        Objects.requireNonNull(operationId, "事实追加动作不能为空。"); Objects.requireNonNull(reference, "事实追加引用不能为空。");
        Objects.requireNonNull(subjectId, "事实主体 UUID 不能为空。"); Objects.requireNonNull(value, "事实内容不能为空。");
        Objects.requireNonNull(source, "事实来源不能为空。");
        if (!reference.scope().equals(source.actor().scope())) { throw new IllegalArgumentException("事实来源与空间引用作用域不一致。"); }
    }
}
