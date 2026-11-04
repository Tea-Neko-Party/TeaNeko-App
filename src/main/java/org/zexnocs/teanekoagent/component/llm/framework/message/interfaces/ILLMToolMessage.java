package org.zexnocs.teanekoagent.component.llm.framework.message.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 大语言模型 tool 消息接口。
 * <br>用于把本地工具执行结果作为上下文返回给模型。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 * @version 4.8.0-l
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
