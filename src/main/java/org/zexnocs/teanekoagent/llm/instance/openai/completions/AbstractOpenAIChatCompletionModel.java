package org.zexnocs.teanekoagent.llm.instance.openai.completions;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.AbstractLLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

/**
 * OpenAI Chat Completions 兼容模型的抽象适配器。
 * <br>子类只需声明 provider、默认模型、endpoint 和供应商参数限制，即可复用消息、工具、JSON 输出及 usage 映射。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.1
 * @version 4.5.0-l
 */
public abstract class AbstractOpenAIChatCompletionModel extends AbstractLLMModel {
    /** API 响应服务。 */
    private final IAPIResponseService apiResponseService;

    private final ICredentialResolver credentialResolver;

    /**
     * 创建 Chat Completions 兼容模型。
     *
     * @param provider 模型适配器 ID
     * @param model 默认模型名称
     * @param baseOptions 模型代码自带的 base options
     * @param apiResponseService API 响应服务
     * @param credentialResolver Spring 凭据与可信连接解析端口
     */
    protected AbstractOpenAIChatCompletionModel(String provider,
                                                String model,
                                                OpenAIChatCompletionModelOptions baseOptions,
                                                IAPIResponseService apiResponseService,
                                                ICredentialResolver credentialResolver) {
        super(provider, model, baseOptions);
        this.apiResponseService = apiResponseService;
        this.credentialResolver = credentialResolver;
    }

    /**
     * 判断通用 Chat Completions 能力和供应商专用约束是否支持给定参数。
     *
     * @param options 模型参数
     * @return 参数能够完整映射时返回 {@code true}
     */
    @Override
    public boolean supports(ILLMModelOptions options) {
        var standardOptions = LLMModelOptions.copyOf(options);
        var providerSupported = standardOptions.findProvider()
                .map(getProvider()::equals)
                .orElse(true);
        var responseFormatSupported = standardOptions.findResponseFormat()
                .map(format -> format == LLMResponseFormat.TEXT || format == LLMResponseFormat.JSON)
                .orElse(true);
        var streamSupported = standardOptions.findStream()
                .map(stream -> !stream)
                .orElse(true);
        return providerSupported
                && responseFormatSupported
                && streamSupported
                && supportsChatCompletionOptions(prepareOptions(standardOptions));
    }

    /**
     * 校验供应商专用参数约束。
     *
     * @param options 已转换的 Chat Completions 参数
     * @return 供应商支持时返回 {@code true}
     */
    protected boolean supportsChatCompletionOptions(OpenAIChatCompletionModelOptions options) {
        if (options.findThinking().isEmpty()) {
            return true;
        }
        var metadata = options.getMetadata();
        return metadata.containsKey(OpenAIChatCompletionModelOptions.EXTRA_BODY_PREFIX + "thinking")
                || metadata.containsKey(OpenAIChatCompletionModelOptions.EXTRA_BODY_PREFIX + "reasoning_effort");
    }

    /**
     * 将合并后的统一参数转换为供应商参数。
     * <br>供应商 options 子类应重写该方法，以便在构造请求前生成 {@code body.} 扩展字段。
     *
     * @param options 已合并的统一参数
     * @return Chat Completions 参数
     */
    protected OpenAIChatCompletionModelOptions prepareOptions(LLMModelOptions options) {
        return OpenAIChatCompletionModelOptions.copyOf(options);
    }

    /**
     * 声明供应商内建 base URL，不读取 Prompt 选项。
     *
     * @return 供应商内建的可信默认服务地址
     */
    protected String defaultBaseUrl() {
        return OpenAIChatCompletionModelOptions.DEFAULT_BASE_URL;
    }

    /**
     * 声明供应商内建 API path，不读取 Prompt 选项。
     *
     * @return 供应商内建的默认站内接口路径
     */
    protected String defaultApiPath() {
        return OpenAIChatCompletionModelOptions.DEFAULT_API_PATH;
    }

    /**
     * 调用 OpenAI Chat Completions 兼容 API。
     *
     * @param prompt 本次调用的 Prompt
     * @param options 已完成合并的模型参数
     * @return 统一 LLM Result Future
     */
    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options) {
        return doCall(prompt, options, null);
    }

    /**
     * 校验有效输入并提交一次模型请求，显式策略向实际 HTTP 资源传递；不在此入口执行工具循环或自动重试。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param options 本次有效模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws IllegalStateException 当执行状态、装配契约或返回的完成信号不符合要求时
     */
    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options, ExecutionPolicy execution) {
        if (execution != null) {
            execution.checkActive();
        }
        var chatOptions = prepareOptions(options);
        var access = credentialResolver.require(getProvider());
        var request = OpenAIChatCompletionMapper.toRequest(prompt, chatOptions, access.apiKey());
        request.setBaseUrl(access.baseUrlOr(defaultBaseUrl()));
        request.setApiPath(access.apiPathOr(defaultApiPath()));
        try {
            if (execution != null) {
                execution.checkActive();
            }
            var response = execution == null
                    ? apiResponseService.addTask(request, OpenAIChatCompletionResponseData.class, true, true)
                    : apiResponseService.addTask(request, OpenAIChatCompletionResponseData.class, true, true, execution);
            return response
                    .thenApply(OpenAIChatCompletionMapper::toResult);
        } catch (ExecutionStoppedException stopped) {
            throw stopped;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "%s chat completion request failed.".formatted(getProvider()),
                    exception
            );
        }
    }

}
