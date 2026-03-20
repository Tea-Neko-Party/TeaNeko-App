package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * LLM 发送的 Message 的类型
 *
 * @author zExNocs
 * @date 2026/03/20
 * @since 4.4.0
 */
@Getter
public enum LLMMessageRole {
    /**
     * 由用户层面发送的消息。
     * <br>主要用于向 LLM 提问
     */
    USER("user"),

    /**
     * 由模型返回的消息。
     */
    ASSISTANT("assistant"),

    /**
     * 用于规范模型输出的消息。
     */
    SYSTEM("system"),

    /**
     * 包含 Function Call 结果的消息。
     */
    TOOL("tool");

    /**
     * 类型转化成 string
     */
    @JsonValue
    private final String value;

    LLMMessageRole(String value) {
        this.value = value;
    }

    /**
     * 用于反序列化。
     *
     * @param value value 值
     * @return {@link LLMMessageRole }
     */
    @JsonCreator
    public static LLMMessageRole fromValue(String value) {
        for (LLMMessageRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
