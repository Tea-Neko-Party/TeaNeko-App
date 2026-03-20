package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

/**
 * 用于返回给 LLM Tool 结果的消息
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMToolMessage extends ILLMMessage {
    /**
     * 返回角色
     *
     * @return {@link LLMMessageRole }
     */
    default LLMMessageRole getRole() {
        return LLMMessageRole.TOOL;
    }
}
