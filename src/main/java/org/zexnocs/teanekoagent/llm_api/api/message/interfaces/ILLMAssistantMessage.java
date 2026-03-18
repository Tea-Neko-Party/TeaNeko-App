package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

/**
 * LLM 回复的消息。
 * <br>LLM 给出的结果
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMAssistantMessage extends ILLMMessage {
    /// assistant 角色
    String ROLE = "assistant";

    /**
     * 返回角色
     *
     * @return {@link String }
     */
    default String getRole() {
        return ROLE;
    }
}
