package org.zexnocs.teanekoagent.llm.framework.message.interfaces;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * LLM 用户发送的消息
 * <br>用于指定 LLM 需要回答的内容
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 * @version 4.9.0-l
 */
public interface ILLMUserMessage extends ILLMMessage {
    /**
     * 返回角色
     *
     * @return {@link LLMMessageRole }
     */
    default LLMMessageRole getRole() {
        return LLMMessageRole.USER;
    }
}
