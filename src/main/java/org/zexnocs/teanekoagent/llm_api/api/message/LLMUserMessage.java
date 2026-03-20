package org.zexnocs.teanekoagent.llm_api.api.message;


import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api.api.message.interfaces.ILLMUserMessage;

/**
 * LLM 用户发送的消息
 * <br>用于指定 LLM 需要回答的内容
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
@SuperBuilder
public class LLMUserMessage extends AbstractLLMMessage implements ILLMUserMessage {
}
