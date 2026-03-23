package org.zexnocs.teanekoagent.llm_api_framework.message;

import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMSystemMessage;

/**
 * LLM 系统消息接口
 * <br>用于规范 LLM 行为的系统级消息
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
@SuperBuilder
public class LLMSystemMessage extends AbstractLLMMessage implements ILLMSystemMessage {
}
