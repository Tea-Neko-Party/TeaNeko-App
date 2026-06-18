package org.zexnocs.teanekoagent_old.llm.framework.message.interfaces;

import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolCall;

import java.util.List;

/**
 * 大语言模型 assistant 消息接口。
 * <br>用于描述模型输出的消息以及可选工具调用。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMAssistantMessage extends ILLMMessage {
    /**
     * 获取消息角色，默认为 ASSISTANT。
     *
     * @return {@link LLMMessageRole }
     */
    default LLMMessageRole getRole() {
        return LLMMessageRole.ASSISTANT;
    }

    /**
     * 获取工具调用列表，默认为空列表。
     *
     * @return {@link List }<{@link ILLMToolCall }>
     */
    default List<ILLMToolCall> getToolCalls() {
        return List.of();
    }
}
