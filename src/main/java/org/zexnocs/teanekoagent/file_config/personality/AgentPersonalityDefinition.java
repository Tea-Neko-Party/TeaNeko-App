package org.zexnocs.teanekoagent.file_config.personality;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 Agent 角色的基础人格定义。
 * <br>该定义通常来自文件配置，作为默认主性格，不应被学习记忆直接覆盖。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentPersonalityDefinition {
    /**
     * 人格 ID。
     */
    private String id = "";

    /**
     * 展示名称。
     */
    private String displayName = "";

    /**
     * 人格定义版本。
     */
    private int version = 1;

    /**
     * 身份设定。
     */
    private Identity identity = new Identity();

    /**
     * 核心特质列表。
     */
    private List<String> coreTraits = new ArrayList<>();

    /**
     * 说话风格列表。
     */
    private List<String> speakingStyle = new ArrayList<>();

    /**
     * 人格边界。
     */
    private Boundaries boundaries = new Boundaries();

    /**
     * 学习策略。
     */
    private LearningPolicy learningPolicy = new LearningPolicy();

    /**
     * 创建内置兜底人格。
     *
     * @param agentId agent ID。
     * @return 兜底人格定义。
     */
    public static AgentPersonalityDefinition fallback(String agentId) {
        var definition = new AgentPersonalityDefinition();
        definition.setId(blankToDefault(agentId, "teaneko"));
        definition.setDisplayName("TeaNeko");
        definition.getIdentity().setName("TeaNeko");
        definition.getIdentity().getImmutable().add("Do not rewrite the agent identity unless a custom personality is enabled.");
        definition.getCoreTraits().add("Reply naturally and directly.");
        definition.getSpeakingStyle().add("Prefer the user's language.");
        definition.getBoundaries().getHard().add("Do not claim that a tool was called unless the runtime actually called it.");
        definition.getLearningPolicy().getImmutableFields().add("identity.name");
        definition.getLearningPolicy().getImmutableFields().add("identity.immutable");
        definition.getLearningPolicy().getImmutableFields().add("boundaries.hard");
        return definition;
    }

    /**
     * 从运行期自定义配置创建基础人格定义。
     *
     * @param config  自定义人格运行配置。
     * @param agentId agent ID。
     * @return 自定义基础人格定义。
     */
    public static AgentPersonalityDefinition fromCustom(AgentPersonalityRuntimeConfig config, String agentId) {
        var definition = new AgentPersonalityDefinition();
        definition.setId(blankToDefault(agentId, "custom"));
        definition.setDisplayName(blankToDefault(config.agentId(), "Custom Agent"));
        definition.getIdentity().setName(definition.getDisplayName());
        definition.getCoreTraits().add(config.customPersonality());
        if (!config.customSpeakingStyle().isBlank()) {
            definition.getSpeakingStyle().add(config.customSpeakingStyle());
        }
        definition.getBoundaries().getHard().addAll(config.customBoundaries());
        definition.getLearningPolicy().getMutableFields().add("user preference");
        definition.getLearningPolicy().getMutableFields().add("relationship");
        definition.getLearningPolicy().getMutableFields().add("speaking detail");
        definition.getLearningPolicy().getImmutableFields().add("identity.name");
        definition.getLearningPolicy().getImmutableFields().add("boundaries.hard");
        return definition;
    }

    /**
     * 将空白值替换为默认值。
     *
     * @param value    原始值。
     * @param fallback 默认值。
     * @return 非空值。
     */
    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * 人格身份设定。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Identity {
        /**
         * 角色名称。
         */
        private String name = "";

        /**
         * 不可变身份约束。
         */
        private List<String> immutable = new ArrayList<>();
    }

    /**
     * 人格边界规则。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Boundaries {
        /**
         * 硬边界规则。
         */
        private List<String> hard = new ArrayList<>();

        /**
         * 软边界规则。
         */
        private List<String> soft = new ArrayList<>();
    }

    /**
     * 人格学习策略。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LearningPolicy {
        /**
         * 允许学习或微调的字段。
         */
        private List<String> mutableFields = new ArrayList<>();

        /**
         * 不允许学习或覆盖的字段。
         */
        private List<String> immutableFields = new ArrayList<>();
    }
}
