package org.zexnocs.teanekoagent.skill;

import java.util.HashSet;
import java.util.List;

/**
 * 从可信本地目录读取的不可变流程描述，工具依赖只收窄能力，不授予权限。
 * @param id 稳定技能名称
 * @param description 有界选择说明
 * @param version 本地声明版本
 * @param enabled 是否允许选入
 * @param requiredTools 必需的本轮工具名称
 * @param instructions 按需加载的 Markdown 正文
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record SkillDefinition(String id, String description, int version, boolean enabled, List<String> requiredTools, String instructions) {
    /**
     * 冻结依赖并限制正文，避免目录无限扩张或工具结果被静默截断。
     * @param id 名称
     * @param description 选择说明
     * @param version 正整数版本
     * @param enabled 启用状态
     * @param requiredTools 必需工具
     * @param instructions 完整正文
     * @throws IllegalArgumentException 当定位、正文或依赖范围无效时
     */
    public SkillDefinition {
        requiredTools = List.copyOf(requiredTools);
        if (!validId(id) || description == null || description.isBlank() || description.length() > 256 || version < 1
                || instructions == null || instructions.isBlank() || instructions.length() > 3000 || requiredTools.size() > 16
                || new HashSet<>(requiredTools).size() != requiredTools.size()
                || requiredTools.stream().anyMatch(name -> !name.matches("[a-zA-Z0-9_-]{1,64}") || name.equals("skill_load"))) {
            throw new IllegalArgumentException("Skill 名称、说明、版本、正文或工具依赖无效。");
        }
    }
    /**
     * 统一配置、目录及命令中的名称语法，不接收路径或类名。
     * @param value 待校验名称
     * @return 是否为合法 Skill 名称
     */
    public static boolean validId(String value) { return value != null && value.matches("[a-z][a-z0-9-]{0,47}"); }
}
