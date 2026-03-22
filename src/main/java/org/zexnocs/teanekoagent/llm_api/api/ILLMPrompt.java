package org.zexnocs.teanekoagent.llm_api.api;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm_api.api.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm_api.api.model.interfaces.ILLMModelOptions;

import java.util.List;

/**
 * LLM 输入的接口。
 *
 * @author zExNocs
 * @date 2026/03/23
 * @since 4.4.0
 */
public interface ILLMPrompt {
    /**
     * 获取 LLM 输入的消息。
     *
     * @return {@link List }<{@link ILLMMessage }>
     */
    @NonNull
    List<ILLMMessage> getMessages();

    /**
     * 获取 LLM Options。
     *
     * @return {@link ILLMModelOptions }
     */
    @Nullable
    ILLMModelOptions getOptions();
}
