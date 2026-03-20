package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

/**
 * LLM Content 数据。
 * <br>具有 type、和 contentPart 特征。
 *
 * @see ITeaNekoContent
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMContent extends ITeaNekoContent {
    /**
     * 获取消息类型
     * @return 消息类型字符串，例如 "input_text"、"output_text" 等
     */
    @NonNull
    @Override
    String getType();

    /**
     * 获取消息内容
     *
     * @return 消息内容对象，具体类型根据消息类型而定。
     */
    @NonNull
    @Override
    ILLMContentPart getContentPart();
}
