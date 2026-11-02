package org.zexnocs.teanekoagent.skill;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * 当前作用域显式允许的 Skill 名称，不包含目录路径、执行器或凭据。
 * @param enabled 当前是否允许使用 Skill
 * @param ids 显式允许列表，不采用全局 all
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record SkillPolicy(boolean enabled, List<String> ids) {
    /**
     * 冻结有界选择，避免同名重复加载。
     * @param enabled 启用状态
     * @param ids 显式名称列表
     * @throws IllegalArgumentException 当数量、名称或重复项非法时
     */
    public SkillPolicy {
        ids = List.copyOf(ids);
        if (ids.size() > 8 || new HashSet<>(ids).size() != ids.size() || ids.stream().anyMatch(id -> !SkillDefinition.validId(id))) {
            throw new IllegalArgumentException("作用域 Skill 列表最多八项，不允许重复或非法名称。");
        }
    }
    /**
     * 解析 /cfg 的逗号列表，空值表示未选入任何 Skill。
     * @param enabled 当前开关
     * @param value 逗号分隔名称
     * @return 已冻结策略
     * @throws IllegalArgumentException 当配置缺失或格式非法时
     */
    public static SkillPolicy parse(boolean enabled, String value) {
        if (value == null || value.length() > 512) { throw new IllegalArgumentException("Skill 配置缺失或过长。"); }
        return new SkillPolicy(enabled, value.isBlank() ? List.of() : Arrays.stream(value.split(",", -1)).map(String::strip).toList());
    }
}
