package org.zexnocs.teanekoagent.llm_api_framework.model;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm_api_framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm_api_framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm_api_framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm_api_framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大语言模型注册与调用服务。
 * <br>扫描所有 {@link ILLMModel} Bean，并通过 {@link LLMModelId} 路由到对应模型。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
@Service
public class LLMModelService extends AbstractScanner implements ILLMModelService {
    /**
     * Spring Bean 扫描器，用于发现容器中注册的 {@link ILLMModel} 实例。
     */
    private final IBeanScanner beanScanner;

    /**
     * 模型注册表。
     * <br>键为模型唯一标识，值为对应的模型适配器实例；使用并发映射以支持扫描和查询过程中的线程安全访问。
     */
    private final Map<LLMModelId, ILLMModel> modelMap = new ConcurrentHashMap<>();

    /**
     * 创建大语言模型注册与调用服务。
     *
     * @param beanScanner Spring Bean 扫描器
     */
    public LLMModelService(IBeanScanner beanScanner) {
        this.beanScanner = beanScanner;
    }

    /**
     * 获取所有已注册的大语言模型实例。
     * <br>返回的是注册表快照，外部调用方不能直接修改内部状态。
     *
     * @return 模型 ID 到模型实例的映射
     */
    @Override
    public Map<LLMModelId, ILLMModel> getModels() {
        return Map.copyOf(modelMap);
    }

    /**
     * 根据模型 ID 获取已注册的大语言模型实例。
     *
     * @param modelId 模型唯一标识
     * @return 对应的大语言模型实例
     * @throws IllegalArgumentException 当模型未注册时抛出
     */
    @Override
    public ILLMModel getModel(LLMModelId modelId) {
        var model = modelMap.get(modelId);
        if (model == null) {
            throw new IllegalArgumentException("LLM model is not registered: " + modelId);
        }
        return model;
    }

    /**
     * 使用指定模型执行一次大语言模型调用。
     * <br>调用前会校验模型是否存在以及本次调用选项是否被该模型支持。
     *
     * @param modelId 模型唯一标识
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果
     * @throws IllegalArgumentException 当模型未注册或调用选项不受支持时抛出
     */
    @Override
    public ITaskResult<ILLMResult> call(LLMModelId modelId, ILLMPrompt prompt) {
        var model = getModel(modelId);
        var options = prompt.getOptions() == null ? model.getDefaultOptions() : prompt.getOptions();
        if (!model.supports(options)) {
            throw new IllegalArgumentException("LLM model does not support the prompt options: " + modelId);
        }
        return model.call(prompt);
    }

    /**
     * 扫描并注册 Spring 容器中的所有大语言模型适配器。
     * <br>当存在重复的 {@link LLMModelId} 时会立即抛出异常，避免调用时路由到不确定的模型实例。
     *
     * @throws IllegalStateException 当发现重复模型 ID 时抛出
     */
    @Override
    protected void _scan() {
        for (var model : beanScanner.getBeansOfType(ILLMModel.class).values()) {
            var modelId = model.getModelId();
            var existing = modelMap.putIfAbsent(modelId, model);
            if (existing != null) {
                throw new IllegalStateException("Duplicate LLM model id: " + modelId);
            }
        }
    }

    /**
     * 清空模型注册表。
     * <br>通常由重新扫描流程调用，用于在重新加载模型 Bean 前释放旧注册信息。
     */
    @Override
    protected void _clear() {
        modelMap.clear();
    }
}
