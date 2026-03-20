package org.zexnocs.teanekoagent.llm_api.api.message.content;

import org.zexnocs.teanekoagent.llm_api.api.message.LLMContent;
import org.zexnocs.teanekoagent.llm_api.api.message.interfaces.ILLMContent;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于快速构造 LLM Content List 的 builder
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
public class LLMContentListBuilder {
    /**
     * 直接获取一个 builder
     *
     * @return {@link LLMContentListBuilder }
     */
    public static LLMContentListBuilder builder() {
        return new LLMContentListBuilder();
    }

    /// 存储的 list
    private final List<ILLMContent> contentList = new ArrayList<>();

    /**
     * 构造出 list
     *
     * @return {@link List }<{@link LLMContent }>
     */
    public List<ILLMContent> build() {
        return List.copyOf(contentList);
    }

    /**
     * 添加一个 {@link TextLLMContentPart}
     *
     * @param text 文本内容
     * @return 当前 builder 对象，以便于链式调用
     */
    public LLMContentListBuilder addText(String text) {
        contentList.add(LLMContent.builder()
                .type("text")
                .contentPart(new TextLLMContentPart(text))
                .build());
        return this;
    }
}
