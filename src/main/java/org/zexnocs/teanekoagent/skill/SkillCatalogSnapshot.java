package org.zexnocs.teanekoagent.skill;

import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.*;

/**
 * 原子发布的完整 Skill 目录，内容指纹覆盖正文，修改正文而忘记改版本也会换代。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class SkillCatalogSnapshot {
    private final Map<String, SkillDefinition> skills;
    private final String fingerprint;
    /**
     * 构建稳定排序及内容指纹，不保留调用方的可变集合。
     * @param definitions 完整候选目录
     * @throws IllegalArgumentException 当目录越界或名称重复时
     */
    public SkillCatalogSnapshot(Collection<SkillDefinition> definitions) {
        if (definitions.size() > 64) { throw new IllegalArgumentException("Skill 目录最多六十四项。"); }
        var indexed = new TreeMap<String, SkillDefinition>(); var parts = new ArrayList<String>();
        for (var skill : definitions) {
            if (indexed.putIfAbsent(skill.id(), skill) != null) { throw new IllegalArgumentException("Skill 目录存在重名。"); }
        }
        for (var skill : indexed.values()) {
            parts.addAll(List.of(skill.id(), skill.description(), Integer.toString(skill.version()), Boolean.toString(skill.enabled()),
                    Integer.toString(skill.requiredTools().size())));
            parts.addAll(skill.requiredTools()); parts.add(skill.instructions());
        }
        skills = Collections.unmodifiableMap(indexed); fingerprint = StableDigest.sha256(parts.toArray(String[]::new));
    }
    /** @return 按名称排序的不可变目录 */
    public Map<String, SkillDefinition> skills() { return skills; }
    /** @return 覆盖完整目录与正文的稳定指纹 */
    public String fingerprint() { return fingerprint; }
}
