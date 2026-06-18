package org.zexnocs.teanekoagent.llm.framework.model;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

/**
 * 大语言模型适配器的抽象基类。
 * <br>封装供应商级 ID、基础模型名称和 base options 的合并逻辑，具体供应商只需要实现实际调用方法。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public abstract class AbstractLLMModel implements ILLMModel {
    /**
     * 当前模型调用使用的大语言模型供应商 ID。
     * <br>该值不参与模型注册，注册 ID 由模型类上的 {@code LLMModel} 注解提供。
     */
    private final String provider;

    /**
     * 当前模型在供应商侧的基础模型名称。
     * <br>例如 {@code deepseek-v4-flash}；文件配置或本次调用 options 可以覆盖该值。
     */
    private final String model;

    /**
     * 当前模型代码自带的基础调用选项。
     * <br>调用时会与 {@link ILLMPrompt#getOptions()} 合并，提示词中的选项优先级更高。
     */
    private final ILLMModelOptions baseOptions;

    /**
     * 使用供应商级 ID 和基础模型名称创建模型适配器。
     * <br>基础调用选项会自动填充 {@code provider} 和 {@code model}。
     *
     * @param provider 模型供应商级 ID
     * @param model 基础模型名称
     */
    protected AbstractLLMModel(String provider, String model) {
        this(provider, model, LLMModelOptions.builder()
                .provider(provider)
                .model(model)
                .build());
    }

    /**
     * 使用供应商级 ID、基础模型名称和基础调用选项创建模型适配器。
     * <br>传入的 base options 会与供应商和模型名称合并，确保模型身份始终与适配器一致。
     *
     * @param provider 模型供应商级 ID
     * @param model 基础模型名称
     * @param baseOptions 模型代码自带的基础调用选项
     */
    protected AbstractLLMModel(String provider, String model, ILLMModelOptions baseOptions) {
        this.provider = provider;
        this.model = model;
        this.baseOptions = LLMModelOptions.merge(
                baseOptions,
                LLMModelOptions.builder().provider(provider).model(model).build()
        );
    }

    /**
     * 获取模型供应商级 ID。
     *
     * @return 模型供应商级 ID
     */
    @Override
    public String getProvider() {
        return provider;
    }

    /**
     * 获取基础模型名称。
     *
     * @return 基础模型名称
     */
    @Override
    public String getModel() {
        return model;
    }

    /**
     * 获取当前模型代码自带的基础调用选项。
     *
     * @return base options
     */
    @Override
    public ILLMModelOptions getBaseOptions() {
        return baseOptions;
    }

    /**
     * 执行一次模型调用。
     * <br>该方法会先合并 base options 和提示词选项，再委托给 {@link #doCall(ILLMPrompt, LLMModelOptions)} 完成实际调用。
     *
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     */
    @Override
    public TaskFuture<ILLMResult> call(ILLMPrompt prompt) {
        return doCall(prompt, LLMModelOptions.merge(baseOptions, prompt.getOptions()));
    }

    /**
     * 执行供应商相关的实际模型调用。
     * <br>具体模型适配器只需要实现该方法，将统一选项转换为供应商 API 请求并返回统一结果。
     *
     * @param prompt 本次调用的提示词与消息
     * @param options 已完成合并的模型调用选项
     * @return 模型调用结果 Future
     */
    protected abstract TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options);
}
