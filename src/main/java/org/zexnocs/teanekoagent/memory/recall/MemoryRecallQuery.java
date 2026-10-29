package org.zexnocs.teanekoagent.memory.recall;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;

import java.time.Instant;
import java.util.*;

/**
 * 确定性查询条件；空集合表示不限，不隐式按调用者或来源 scope 隔离共享空间。
 * @param subjectId 可选主体筛选
 * @param sourceScope 可选明确来源筛选
 * @param kinds 允许的内容类别
 * @param tags 必须全部存在的原标签
 * @param keywords 必须分别命中正文或标签的字面关键词，不解释正则或查询语言
 * @param start 可选事件查询起点
 * @param end 可选事件查询终点
 * @param includeEstimated 是否允许未知事件按记录时间估算
 * @param excludedSubjects 明确排除的主体，用于组合本人画像和其他主体证据而不重复占预算
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryRecallQuery(@Nullable UUID subjectId, @Nullable AgentScope sourceScope, Set<MemoryFactValue.Kind> kinds,
        Set<String> tags, List<String> keywords, @Nullable Instant start, @Nullable Instant end, boolean includeEstimated, Set<UUID> excludedSubjects) {
    /**
     * 保留既有无主体排除条件的查询方式。
     * @param subjectId 可选主体
     * @param sourceScope 可选来源
     * @param kinds 类别
     * @param tags 标签
     * @param keywords 字面关键词
     * @param start 起点
     * @param end 终点
     * @param includeEstimated 是否允许记录时间估算
     */
    public MemoryRecallQuery(@Nullable UUID subjectId, @Nullable AgentScope sourceScope, Set<MemoryFactValue.Kind> kinds,
            Set<String> tags, List<String> keywords, @Nullable Instant start, @Nullable Instant end, boolean includeEstimated) {
        this(subjectId, sourceScope, kinds, tags, keywords, start, end, includeEstimated, Set.of());
    }
    /**
     * 固定条件并限制筛选成本，关键词按 ROOT 小写进行字面匹配。
     * @param subjectId 主体
     * @param sourceScope 来源
     * @param kinds 类别
     * @param tags 标签
     * @param keywords 字面关键词
     * @param start 起点
     * @param end 终点
     * @param includeEstimated 是否允许估算
     * @param excludedSubjects 明确排除的主体，最多一百个
     * @throws NullPointerException 当集合或元素为空时
     * @throws IllegalArgumentException 当条件容量或时间顺序无效时
     */
    public MemoryRecallQuery {
        kinds = Set.copyOf(kinds); tags = Set.copyOf(tags); keywords = List.copyOf(keywords);
        excludedSubjects = Set.copyOf(excludedSubjects);
        if (excludedSubjects.size() > 100 || tags.size() > 16 || tags.stream().anyMatch(tag -> tag.isBlank() || tag.length() > 64)
                || keywords.size() > 8 || keywords.stream().anyMatch(word -> word.isBlank() || word.length() > 128)
                || start != null && end != null && end.isBefore(start)) { throw new IllegalArgumentException("记忆查询条件容量或时间顺序无效。"); }
        keywords = keywords.stream().map(word -> word.strip().toLowerCase(Locale.ROOT)).distinct().toList();
    }

    /**
     * 构造共享空间内无额外筛选的查询，仍排除过期证据。
     * @return 允许记录时间估算的查询
     */
    public static MemoryRecallQuery all() { return new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(), null, null, true); }

    /**
     * 在一次查询固定时刻判断证据，按需构建哈希标签集合以避免多标签的重复线性查找。
     * 不搜索审计原文或身份文字，未指定标签时不创建集合。
     * @param evidence 已验证的证据
     * @param now 查询固定时刻
     * @return 全部条件及有效期满足时为真
     */
    public boolean matches(MemoryRecallEvidence evidence, Instant now) {
        var value = evidence.value(); var expiry = value.expiresAt();
        if (expiry != null && !expiry.isAfter(now) || excludedSubjects.contains(evidence.subjectId()) || subjectId != null && !subjectId.equals(evidence.subjectId())
                || sourceScope != null && !sourceScope.equals(evidence.source().actor().scope())
                || !kinds.isEmpty() && !kinds.contains(value.kind()) || !tags.isEmpty() && !new HashSet<>(value.tags()).containsAll(tags)
                || !value.eventTime().overlaps(start, end, evidence.createdAt(), includeEstimated)) { return false; }
        var text = value.text().toLowerCase(Locale.ROOT);
        var searchableTags = value.tags().stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList();
        return keywords.stream().allMatch(word -> text.contains(word) || searchableTags.stream().anyMatch(tag -> tag.contains(word)));
    }
}
