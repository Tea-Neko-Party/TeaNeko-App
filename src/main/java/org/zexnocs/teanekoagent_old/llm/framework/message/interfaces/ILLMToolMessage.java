package org.zexnocs.teanekoagent_old.llm.framework.message.interfaces;

/**
 * 大语言模型 tool 消息接口。
 * <br>用于把本地工具执行结果作为上下文返回给模型。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMToolMessage extends ILLMMessage {
    default LLMMessageRole getRole() {
        return LLMMessageRole.TOOL;
    }

    /**
     * 获取工具调用 ID。
     *
     * @return {@link String }
     */
    default String getToolCallId() {
        return null;
    }

    /**
     * 获取工具名字
     *
     * @return {@link String }
     */
    default String getName() {
        return "";
    }
}
