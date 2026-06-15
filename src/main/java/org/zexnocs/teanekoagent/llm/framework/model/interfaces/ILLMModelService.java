package org.zexnocs.teanekoagent.llm.framework.model.interfaces;

import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型服务接口。
 * <br>用于查询已注册模型适配器，并统一发起模型调用。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public interface ILLMModelService {
    /**
     * 获取所有已注册的大语言模型适配器实例。
     *
     * @return 模型适配器 ID 到模型实例的映射
     */
    Map<LLMModelId, ILLMModel> getModels();

    /**
     * 获取文件配置中声明的默认模型适配器 ID。
     *
     * @return 默认模型适配器 ID
     * @throws IllegalStateException 当文件配置未声明默认模型适配器 ID 时抛出
     */
    LLMModelId getDefaultModelId();

    /**
     * 获取指定模型适配器的默认 options。
     * <br>返回值由模型 base options 与文件配置 default options 合并得到。
     *
     * @param modelId 模型适配器 ID
     * @return 合并后的默认 options
     */
    LLMModelOptions getDefaultOptions(LLMModelId modelId);

    /**
     * 根据模型适配器 ID 获取已注册的大语言模型实例。
     *
     * @param modelId 模型适配器 ID
     * @return 对应的大语言模型实例
     * @throws IllegalArgumentException 当模型未注册时抛出
     */
    ILLMModel getModel(LLMModelId modelId);

    /**
     * 根据模型适配器 ID 获取已注册的大语言模型实例。
     *
     * @param modelId 模型适配器 ID，通常为供应商 ID
     * @return 对应的大语言模型实例
     * @throws IllegalArgumentException 当模型未注册时抛出
     */
    default ILLMModel getModel(String modelId) {
        return getModel(LLMModelId.of(modelId));
    }

    /**
     * 根据供应商 ID 获取已注册的大语言模型适配器实例。
     * <br>该重载保留给旧调用代码迁移使用；{@code model} 不参与适配器路由。
     *
     * @param provider 模型供应商 ID
     * @param model 模型名称
     * @return 对应的大语言模型适配器实例
     * @throws IllegalArgumentException 当模型适配器未注册时抛出
     * @deprecated 模型名称不再属于 {@link LLMModelId}，请使用 {@link #getModel(String)}。
     */
    @Deprecated
    default ILLMModel getModel(String provider, String model) {
        return getModel(provider);
    }

    /**
     * 使用指定模型适配器执行一次大语言模型调用。
     *
     * @param modelId 模型适配器 ID
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     * @throws IllegalArgumentException 当模型未注册或调用选项不受支持时抛出
     */
    TaskFuture<ILLMResult> call(LLMModelId modelId, ILLMPrompt prompt);

    /**
     * 使用默认模型适配器执行一次大语言模型调用。
     * <br>如果 prompt options 中提供 provider，则优先使用 prompt 指定的供应商级 ID；model 只覆盖本次调用的模型名称。
     *
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     * @throws IllegalStateException 当未配置默认模型适配器且 prompt 未提供 provider 时抛出
     */
    TaskFuture<ILLMResult> call(ILLMPrompt prompt);

    /**
     * 使用默认模型和消息列表执行一次大语言模型调用。
     *
     * @param messages 消息列表
     * @return 模型调用结果 Future
     */
    default TaskFuture<ILLMResult> call(List<ILLMMessage> messages) {
        return call(new LLMPrompt(messages));
    }

    /**
     * 使用指定模型适配器 ID 执行一次大语言模型调用。
     *
     * @param modelId 模型适配器 ID，通常为供应商 ID
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     * @throws IllegalArgumentException 当模型适配器未注册或调用选项不受支持时抛出
     */
    default TaskFuture<ILLMResult> call(String modelId, ILLMPrompt prompt) {
        return call(LLMModelId.of(modelId), prompt);
    }

    /**
     * 使用指定供应商和模型名称执行一次大语言模型调用。
     * <br>{@code provider} 用于选择适配器，{@code model} 写入本次调用 options，不参与适配器注册 ID。
     *
     * @param provider 模型供应商 ID
     * @param model 模型名称
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     * @throws IllegalArgumentException 当模型未注册或调用选项不受支持时抛出
     */
    default TaskFuture<ILLMResult> call(String provider, String model, ILLMPrompt prompt) {
        var options = LLMModelOptions.merge(
                prompt.getOptions(),
                LLMModelOptions.builder()
                        .provider(provider)
                        .model(model)
                        .build()
        );
        return call(LLMModelId.of(provider), new LLMPrompt(prompt.getMessages(), options));
    }
}
