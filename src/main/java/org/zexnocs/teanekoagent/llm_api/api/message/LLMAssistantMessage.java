package org.zexnocs.teanekoagent.llm_api.api.message;

import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api.api.message.interfaces.ILLMAssistantMessage;

/**
 * LLM 回复的消息，即 LLM 给出的结果
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
@SuperBuilder
public class LLMAssistantMessage extends AbstractLLMMessage implements ILLMAssistantMessage {
}
