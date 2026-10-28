package org.zexnocs.teanekoagent.llm.framework.model;

import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

/**
 * 大语言模型适配器的抽象基类。
 * <br>封装供应商级 ID、基础模型名称和 base options 的合并逻辑，具体供应商只需要实现实际调用方法。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
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
     * @return 适配器的内建基础选项，不包含文件或单次请求覆盖
     */
    @Override
    public ILLMModelOptions getBaseOptions() {
        return baseOptions;
    }

    /**
     * 执行一次模型调用。
     * 普通输入合并基础值，已解析输入保留缺失值，再委托给供应商调用。
     *
     * @param prompt 本次调用的提示词、消息与调用选项
     * @return 模型调用结果 Future
     */
    @Override
    public TaskFuture<ILLMResult> call(ILLMPrompt prompt) {
        return callInternal(prompt, null);
    }

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    @Override
    public TaskFuture<ILLMResult> call(ILLMPrompt prompt, ExecutionPolicy execution) {
        java.util.Objects.requireNonNull(execution, "execution").checkActive();
        return callInternal(prompt, execution);
    }

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private TaskFuture<ILLMResult> callInternal(ILLMPrompt prompt, ExecutionPolicy execution) {
        LLMOptionBoundary.validate(prompt.getOptions());
        var options = prompt.getOptionsPolicy() == LLMOptionsPolicy.RESOLVED
                ? LLMOptionBoundary.requireResolved(prompt.getOptions())
                : LLMModelOptions.merge(baseOptions, prompt.getOptions());
        LLMOptionBoundary.validate(options);
        if (!supports(options)) {
            throw new IllegalArgumentException("模型不支持当前输入参数：" + provider);
        }
        LLMInputValidator.validate(prompt.getMessages(), getCapabilities(options.getModel()));
        return execution == null ? doCall(prompt, options) : doCall(prompt, options, execution);
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

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param options 本次有效模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws UnsupportedOperationException 当该入口或适配器尚不支持请求的执行方式时
     */
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options, ExecutionPolicy execution) {
        throw new UnsupportedOperationException("LLM adapter does not support execution policies.");
    }
}
