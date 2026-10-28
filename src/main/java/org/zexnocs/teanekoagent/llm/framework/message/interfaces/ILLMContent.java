package org.zexnocs.teanekoagent.llm.framework.message.interfaces;

import org.jspecify.annotations.NonNull;

/**
 * LLM Content 数据。
 * <br>具有 type、和 contentPart 特征。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
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
