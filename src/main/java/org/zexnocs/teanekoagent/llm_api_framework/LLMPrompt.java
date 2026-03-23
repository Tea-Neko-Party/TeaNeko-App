package org.zexnocs.teanekoagent.llm_api_framework;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm_api_framework.model.interfaces.ILLMModelOptions;

import java.util.List;

/**
 * LLM Prompt 基本实现，作为任何 LLM 输入的接口。
 *
 * @author zExNocs
 * @date 2026/03/23
 * @since 4.4.0
 */
@AllArgsConstructor
public class LLMPrompt implements ILLMPrompt {
    /// LLM 输入的消息
    @Getter
    private final List<ILLMMessage> messages;

    /// LLM 输入选项
    @Getter
    @Nullable
    private final ILLMModelOptions options;

    public LLMPrompt(List<ILLMMessage> messages) {
        this.messages = messages;
        this.options = null;
    }

    public LLMPrompt(ILLMMessage message) {
        this.messages = List.of(message);
        this.options = null;
    }

    public LLMPrompt(ILLMMessage message, @Nullable ILLMModelOptions options) {
        this.messages = List.of(message);
        this.options = options;
    }
}
