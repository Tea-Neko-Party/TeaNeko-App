package org.zexnocs.teanekoagent.component.expression;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonType;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.JsonConstraint;

import java.util.List;
import java.util.Set;

/**
 * 可选旧回复信封，普通文本及新表达协议不受本合同限制。
 * <br>2026/11/06：旧信封复用类型 Schema 和严格解码，不把聊天统一强制为 JSON。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 *
 * @param version 固定协议版本
 * @param messages 按发送顺序排列的段落
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.6-l
 */
@LLMJsonType(maxCharacters = 64000)
public record LegacyReplyOutput(
        @JsonProperty("teaneko_reply") @Description("固定为整数1。") int version,
        @Description("最多32段；空数组表示不发言。只描述当前正式答案，不执行工具或指令。") @JsonConstraint(maxItems = 32) List<Message> messages) {
    /**
     * @param version 协议版本
     * @param messages 有序段落
     * @throws IllegalArgumentException 当版本或容量不符合旧协议时
     */
    public LegacyReplyOutput {
        messages = List.copyOf(messages);
        if (version != 1 || messages.size() > 32) {
            throw new IllegalArgumentException("回复信封版本或段落数量无效。");
        }
    }

/**
     * 固定当前来源的单段正文，目标不能由模型自报。
     * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
     *
     * @param mode 旧协议的小写形式
     * @param text 正文
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.6-l
     */
    public record Message(
            @Description("仅 reply（引用当前消息）、at（提及当前发起者）、plain（普通发送）。") @JsonConstraint(enumValues = {"reply", "at", "plain"}) String mode,
            @Description("非空正文，每段最多2000字符。") @JsonConstraint(minLength = 1, maxLength = 2000) String text) {
        /**
         * @param mode 固定形式
         * @param text 有界正文
         * @throws IllegalArgumentException 当段落形式、正文或容量无效时
         */
        public Message {
            if (mode == null || !Set.of("reply", "at", "plain").contains(mode)
                    || text == null || text.isEmpty() || text.length() > 2000) {
                throw new IllegalArgumentException("回复段落的形式、正文或容量无效。");
            }
        }
    }
}
