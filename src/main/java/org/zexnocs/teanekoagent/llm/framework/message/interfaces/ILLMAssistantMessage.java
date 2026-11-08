package org.zexnocs.teanekoagent.llm.framework.message.interfaces;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.util.List;

/**
 * 大语言模型 assistant 消息接口。
 * <br>用于描述模型输出的消息以及可选工具调用。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：修正工具调用返回说明中的无效 HTML。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 * @version 4.9.0-l
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
     * 2026/11/05：使用元素类型链接描述返回值，避免无效泛型 HTML。
     *
     * @return 当前消息包含的 {@link ILLMToolCall} 列表
     */
    default List<ILLMToolCall> getToolCalls() {
        return List.of();
    }
}
