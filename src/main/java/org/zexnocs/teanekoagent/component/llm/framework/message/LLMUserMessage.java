package org.zexnocs.teanekoagent.component.llm.framework.message;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。


import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMUserMessage;

/**
 * LLM 用户发送的消息
 * <br>用于指定 LLM 需要回答的内容
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 * @version 4.8.0-l
 */
@SuperBuilder
public class LLMUserMessage extends AbstractLLMMessage implements ILLMUserMessage {
}
