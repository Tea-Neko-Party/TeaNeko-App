package org.zexnocs.teanekoagent.llm_api_framework.model.interfaces;

import org.zexnocs.teanekoagent.llm_api_framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm_api_framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm_api_framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm_api_framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm_api_framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.util.List;

/**
 * 大语言模型的客户端接口。
 * <br>新的模型供应商只需要实现该接口并注册为 Spring Bean，即可接入 TeaNeko Agent。
 *
 * @author zExNocs
 * @date 2026/03/23
 * @since 4.4.0
 */
public interface ILLMModel {
    /**
     * 获取模型供应商
     *
     * @return {@link String }
     */
    String getProvider();

    /**
     * 获取模型名
     *
     * @return {@link String }
     */
    String getModel();

    /**
     * 获取模型的唯一标识符
     *
     * @return {@link LLMModelId }
     */
    default LLMModelId getModelId() {
        return LLMModelId.of(getProvider(), getModel());
    }

    /**
     * 获取模型的默认配置
     *
     * @return {@link ILLMModelOptions }
     */
    default ILLMModelOptions getDefaultOptions() {
        return LLMModelOptions.builder()
                .provider(getProvider())
                .model(getModel())
                .build();
    }

    /**
     * 是否支持 Options
     *
     * @param options 模型 option
     * @return boolean
     */
    default boolean supports(ILLMModelOptions options) {
        return true;
    }

    /**
     * 使用一个 prompt 来获取模型结果。
     *
     * @param prompt 提示词
     * @return {@link ILLMResult }
     */
    ITaskResult<ILLMResult> call(ILLMPrompt prompt);

    /**
     * 直接使用 {@link ILLMMessage} list 来获取模型结果，使用默认配置。
     *
     * @param messages messages
     * @return {@link ILLMResult }
     */
    default ITaskResult<ILLMResult> call(List<ILLMMessage> messages) {
        return call(new LLMPrompt(messages, getDefaultOptions()));
    }

    /**
     * 使用{@link ILLMMessage} list 和指定的 options 来获取模型结果。
     *
     * @param messages messages
     * @param options options
     * @return {@link ILLMResult }
     */
    default ITaskResult<ILLMResult> call(List<ILLMMessage> messages, ILLMModelOptions options) {
        return call(new LLMPrompt(messages, options));
    }
}
