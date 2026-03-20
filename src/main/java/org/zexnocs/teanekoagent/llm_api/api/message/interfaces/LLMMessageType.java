package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

import lombok.Getter;

/**
 * LLM 发送的 Message 的类型
 *
 * @author zExNocs
 * @date 2026/03/20
 * @since 4.4.0
 */
@Getter
public enum LLMMessageType {
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
    private final String value;

    LLMMessageType(String value) {
        this.value = value;
    }
}
