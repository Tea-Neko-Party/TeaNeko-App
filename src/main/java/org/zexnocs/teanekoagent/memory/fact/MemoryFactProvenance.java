package org.zexnocs.teanekoagent.memory.fact;

import java.util.Objects;
import java.util.Set;

/**
 * 外部证据的不可变原文及转换说明，仅用于可信审计，不直接注入 Prompt。
 * @param system 来源系统及格式
 * @param target 外部物理目标
 * @param key 外部物理键
 * @param externalId 原记录标识
 * @param digest 原记录 JSON 的摘要
 * @param originalJson 原记录 JSON，保留原来源、主体及时间字段
 * @param adjustments 已明确执行的兼容调整
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryFactProvenance(String system, String target, String key, String externalId,
        String digest, String originalJson, Set<String> adjustments) {
    /**
     * 冻结审计说明，不将来源文字解释成权限或指令。
     * @param system 来源格式
     * @param target 来源目标
     * @param key 来源键
     * @param externalId 原标识
     * @param digest 原文摘要
     * @param originalJson 原文
     * @param adjustments 调整说明
     * @throws IllegalArgumentException 当来源定位或摘要无效时
     * @throws NullPointerException 当必需字段为空时
     */
    public MemoryFactProvenance {
        Objects.requireNonNull(system, "外部来源格式不能为空。"); Objects.requireNonNull(target, "外部来源目标不能为空。");
        Objects.requireNonNull(key, "外部来源键不能为空。"); Objects.requireNonNull(externalId, "外部记录标识不能为空。");
        Objects.requireNonNull(digest, "外部记录摘要不能为空。"); Objects.requireNonNull(originalJson, "外部记录原文不能为空。");
        adjustments = Set.copyOf(adjustments);
        if (system.isBlank() || target.isBlank() || key.isBlank() || externalId.isBlank() || !digest.matches("[0-9a-f]{64}") || originalJson.isBlank()) {
            throw new IllegalArgumentException("外部证据定位、摘要或原文无效。");
        }
    }
}
