package org.zexnocs.teanekoagent.component.personality;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonType;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.JsonConstraint;

/**
 * 独立人格学习的完整 JSON 合同，不包含来源、权限或采用指令。
 * <br>2026/11/06：用显式 null 表示不学习，避免空对象与固定字段协议冲突。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 *
 * @param adjustment 表达候选；没有可靠依据时为 null
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.6-l
 */
@LLMJsonType(maxCharacters = 4096)
public record PersonalityLearningOutput(
        @Description("没有明确依据时为 null；否则仅提出一条表达候选，不修改基础角色身份。")
        @Nullable Adjustment adjustment) {
/**
     * 一条有界表达观察，实际字段白名单由提交方复核。
     * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
     *
     * @param field 宿主允许的字段
     * @param text 表达特征
     * @param confidence 置信度
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.6-l
     */
    public record Adjustment(
            @Description("只从 allowedFields 中选择一个字段。") @JsonConstraint(minLength = 1) String field,
            @Description("真实表达特征，非空且最多512字；不包含用户画像、身份、权限或密钥。") @JsonConstraint(minLength = 1, maxLength = 512) String text,
            @Description("有限置信度，0至1。") double confidence) {
        /**
         * @param field 字段名
         * @param text 非空描述
         * @param confidence 有限分数
         * @throws IllegalArgumentException 当候选结构或容量无效时
         */
        public Adjustment {
            if (field == null || field.isBlank() || text == null || text.isBlank() || text.length() > 512
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("人格学习候选的字段、内容或置信度无效。");
            }
        }
    }
}
