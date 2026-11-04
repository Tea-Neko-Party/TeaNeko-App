package org.zexnocs.teanekoagent.component.llm.framework.message.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.NonNull;

/**
 * LLM Content 数据。
 * <br>具有 type、和 contentPart 特征。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.8.0-l
 */
public interface ILLMContent {
    /**
     * 获取消息类型
     *
     * @return 消息类型字符串，例如 "input_text"、"output_text" 等
     */
    @NonNull
    String getType();

    /**
     * 获取消息内容
     *
     * @return 消息内容对象，具体类型根据消息类型而定。
     */
    @NonNull
    ILLMContentPart getContentPart();
}
