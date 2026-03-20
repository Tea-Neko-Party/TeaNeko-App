package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

/**
 * LLM 系统消息接口
 * <br>用于规范 LLM 行为的系统级消息
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMSystemMessage extends ILLMMessage {
    /**
     * 返回角色
     *
     * @return {@link LLMMessageRole }
     */
    default LLMMessageRole getRole() {
        return LLMMessageRole.SYSTEM;
    }
}
