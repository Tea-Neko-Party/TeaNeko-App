package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

/**
 * 从 LLM 接收或者发送给 LLM 作为上下文的消息。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMMessage {
    /**
     * 消息角色
     *
     * @return {@link String}
     */
    String getRole();

    /**
     * 消息内容
     *
     * @return {@link String }
     */
    String getContent();
}
