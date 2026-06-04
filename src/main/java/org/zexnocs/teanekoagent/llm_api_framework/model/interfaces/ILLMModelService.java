package org.zexnocs.teanekoagent.llm_api_framework.model.interfaces;

import org.zexnocs.teanekoagent.llm_api_framework.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm_api_framework.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm_api_framework.model.LLMModelId;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.util.Map;

/**
 * 大语言模型服务接口。
 * <br>用于查询已注册模型，并统一发起模型调用。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public interface ILLMModelService {
    /**
     * 获取所有已注册的大语言模型实例。
     *
     * @return 模型 ID 到模型实例的映射
     */
    Map<LLMModelId, ILLMModel> getModels();

    /**
     * 根据模型 ID 获取已注册的大语言模型实例。
     *
     * @param modelId 模型唯一标识
     * @return 对应的大语言模型实例
     * @throws IllegalArgumentException 当模型未注册时抛出
     */
    ILLMModel getModel(LLMModelId modelId);

    /**
     * 根据供应商和模型名称获取已注册的大语言模型实例。
     *
     * @param provider 模型供应商名称
     * @param model 模型名称
     * @return 对应的大语言模型实例
     * @throws IllegalArgumentException 当模型未注册时抛出
     */
    default ILLMModel getModel(String provider, String model) {
        return getModel(LLMModelId.of(provider, model));
    }

    /**
     * 使用指定模型执行一次大语言模型调用。
     *
     * @param modelId 模型唯一标识
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果
     * @throws IllegalArgumentException 当模型未注册或调用选项不受支持时抛出
     */
    ITaskResult<ILLMResult> call(LLMModelId modelId, ILLMPrompt prompt);

    /**
     * 使用指定供应商和模型名称执行一次大语言模型调用。
     *
     * @param provider 模型供应商名称
     * @param model 模型名称
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果
     * @throws IllegalArgumentException 当模型未注册或调用选项不受支持时抛出
     */
    default ITaskResult<ILLMResult> call(String provider, String model, ILLMPrompt prompt) {
        return call(LLMModelId.of(provider, model), prompt);
    }
}
