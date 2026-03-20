package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

import java.util.Map;

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
     * 获取消息内容
     *
     * @return 消息内容对象，具体类型根据消息类型而定。
     */
    @NonNull
    @Override
    ILLMContentPart getContentPart();

    /**
     * 获取 metadata
     *
     * @return metadata
     */
    @NonNull Map<String, Object> getMetadata();
}
