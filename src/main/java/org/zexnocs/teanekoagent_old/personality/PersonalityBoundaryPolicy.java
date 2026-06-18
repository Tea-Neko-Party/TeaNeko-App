package org.zexnocs.teanekoagent_old.personality;

import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityDefinition;
import org.zexnocs.teanekoagent_old.memory.PersonalityDeltaRecord;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * 从 active base personality 派生的人格边界策略。
 * <br>该策略用于判断学习到的性格修正是否可以注入 prompt。
 *
 * @param immutableFields 不可学习或不可覆盖的字段。
 * @param mutableFields   允许学习的字段或领域。
 * @param hardBoundaries  硬边界规则。
 * @param softBoundaries  软边界规则。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record PersonalityBoundaryPolicy(
        List<String> immutableFields,
        List<String> mutableFields,
        List<String> hardBoundaries,
        List<String> softBoundaries
) {
    /**
     * 创建人格边界策略。
     *
     * @param immutableFields 不可学习或不可覆盖的字段。
     * @param mutableFields   允许学习的字段或领域。
     * @param hardBoundaries  硬边界规则。
     * @param softBoundaries  软边界规则。
     */
    public PersonalityBoundaryPolicy {
        immutableFields = immutableFields == null ? List.of() : List.copyOf(immutableFields);
        mutableFields = mutableFields == null ? List.of() : List.copyOf(mutableFields);
        hardBoundaries = hardBoundaries == null ? List.of() : List.copyOf(hardBoundaries);
        softBoundaries = softBoundaries == null ? List.of() : List.copyOf(softBoundaries);
    }

    /**
     * 从人格定义中创建边界策略。
     *
     * @param definition 人格定义。
     * @return 人格边界策略。
     */
    public static PersonalityBoundaryPolicy from(AgentPersonalityDefinition definition) {
        return new PersonalityBoundaryPolicy(
                definition.getLearningPolicy().getImmutableFields(),
                definition.getLearningPolicy().getMutableFields(),
                definition.getBoundaries().getHard(),
                definition.getBoundaries().getSoft());
    }

    /**
     * 判断一个性格修正是否允许进入 prompt。
     *
     * @param record 性格修正记录。
     * @return 如果允许注入则返回 true。
     */
    public boolean accepts(PersonalityDeltaRecord record) {
        if (record == null || record.isExpired(Instant.now()) || record.getContent().isBlank()) {
            return false;
        }
        var normalizedField = normalize(record.getField());
        var immutable = new HashSet<>(immutableFields.stream().map(PersonalityBoundaryPolicy::normalize).toList());
        if (immutable.contains(normalizedField)) {
            return false;
        }
        if (containsHardBoundaryKeyword(record.getContent())) {
            return false;
        }
        return record.getConfidence() >= 0.2 || record.isLocked();
    }

    /**
     * 判断文本是否包含明显违反硬边界的关键词。
     *
     * @param content 待检查文本。
     * @return 如果包含硬边界关键词则返回 true。
     */
    private boolean containsHardBoundaryKeyword(String content) {
        var text = content.toLowerCase(Locale.ROOT);
        return text.contains("override runtime")
                || text.contains("ignore tool")
                || text.contains("system prompt")
                || text.contains("change identity")
                || text.contains("forget identity");
    }

    /**
     * 规范化字段名。
     *
     * @param value 原始字段名。
     * @return 规范化字段名。
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
