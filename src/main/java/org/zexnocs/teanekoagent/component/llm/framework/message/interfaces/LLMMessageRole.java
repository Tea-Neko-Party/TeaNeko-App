package org.zexnocs.teanekoagent.component.llm.framework.message.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * LLM 发送的 Message 的类型
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/03/20
 * @since 4.4.0
 * @version 4.8.0-l
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
