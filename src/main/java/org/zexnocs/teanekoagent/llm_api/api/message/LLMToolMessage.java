package org.zexnocs.teanekoagent.llm_api.api.message;

import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api.api.message.interfaces.ILLMToolMessage;

/**
 * 用于返回给 LLM Tool 结果的消息
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
@SuperBuilder
public class LLMToolMessage extends AbstractLLMMessage implements ILLMToolMessage {
}
