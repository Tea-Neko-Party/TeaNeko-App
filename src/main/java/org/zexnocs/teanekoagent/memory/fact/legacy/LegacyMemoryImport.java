package org.zexnocs.teanekoagent.memory.fact.legacy;

import org.zexnocs.teanekoagent.memory.fact.MemoryFactSource;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 可信宿主确认的旧行到新空间、TeaNeko UUID 的完整映射，非模型参数。
 * @param operationId 导入请求幂等键
 * @param reference 目标固定引用
 * @param location 旧物理定位
 * @param sourceVersion 预览时的行版本
 * @param sourceDigest 预览时的完整原文摘要
 * @param subjects 旧主体字符串到真实 UUID 的明确映射
 * @param source 真实导入操作者来源
 * @param fallbackRecordedAt 预览与导入共用的缺失时间估算
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record LegacyMemoryImport(UUID operationId, MemoryNamespaceReference reference, LegacyMemoryLocation location,
        long sourceVersion, String sourceDigest, Map<String, UUID> subjects, MemoryFactSource source, Instant fallbackRecordedAt) {
    /**
     * 固定映射和来源，禁止导入者 scope 与目标引用不一致。
     * @param operationId 幂等键
     * @param reference 目标引用
     * @param location 旧定位
     * @param sourceVersion 预期行版本
     * @param sourceDigest 预期原文摘要
     * @param subjects 明确主体映射
     * @param source 可信导入来源
     * @param fallbackRecordedAt 固定估算时刻
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当版本、摘要、映射或来源 scope 无效时
     */
    public LegacyMemoryImport {
        Objects.requireNonNull(operationId, "导入操作 ID 不能为空。"); Objects.requireNonNull(reference, "导入目标引用不能为空。");
        Objects.requireNonNull(location, "导入旧定位不能为空。"); Objects.requireNonNull(sourceDigest, "导入原文摘要不能为空。");
        Objects.requireNonNull(source, "导入操作者不能为空。"); Objects.requireNonNull(fallbackRecordedAt, "导入估算时刻不能为空。");
        subjects = Map.copyOf(subjects);
        if (sourceVersion < 0 || !sourceDigest.matches("[0-9a-f]{64}") || subjects.keySet().stream().anyMatch(String::isBlank)
                || !reference.scope().equals(source.actor().scope())) { throw new IllegalArgumentException("导入版本、摘要、主体映射或来源 scope 无效。"); }
    }
}
