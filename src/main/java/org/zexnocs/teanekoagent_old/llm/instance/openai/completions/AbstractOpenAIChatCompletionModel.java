package org.zexnocs.teanekoagent_old.llm.instance.openai.completions;

import org.zexnocs.teanekoagent_old.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.model.AbstractLLMModel;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent_old.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.Map;
import java.util.Optional;

/**
 * OpenAI Chat Completions 兼容模型的抽象适配器。
 * <br>子类只需声明 provider、默认模型、endpoint 和供应商参数限制，即可复用消息、工具、JSON 输出及 usage 映射。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public abstract class AbstractOpenAIChatCompletionModel extends AbstractLLMModel {
    /** API key 在 metadata 中的字段名。 */
    public static final String API_KEY_METADATA = "apiKey";

    /** API 响应服务。 */
    private final IAPIResponseService apiResponseService;

    /**
     * 创建 Chat Completions 兼容模型。
     *
     * @param provider 模型适配器 ID
     * @param model 默认模型名称
     * @param baseOptions 模型代码自带的 base options
     * @param apiResponseService API 响应服务
     */
    protected AbstractOpenAIChatCompletionModel(String provider,
                                                String model,
                                                OpenAIChatCompletionModelOptions baseOptions,
                                                IAPIResponseService apiResponseService) {
        super(provider, model, baseOptions);
        this.apiResponseService = apiResponseService;
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
     * 解析本次请求的 base URL。
     *
     * @param options Chat Completions 参数
     * @return base URL
     */
    protected String resolveBaseUrl(OpenAIChatCompletionModelOptions options) {
        return options.findBaseUrl().orElse(OpenAIChatCompletionModelOptions.DEFAULT_BASE_URL);
    }

    /**
     * 解析本次请求的 API path。
     *
     * @param options Chat Completions 参数
     * @return API path
     */
    protected String resolveApiPath(OpenAIChatCompletionModelOptions options) {
        return options.findApiPath().orElse(OpenAIChatCompletionModelOptions.DEFAULT_API_PATH);
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
        var chatOptions = prepareOptions(options);
        var apiKey = requireMetadata(chatOptions.getMetadata(), API_KEY_METADATA);
        var request = OpenAIChatCompletionMapper.toRequest(prompt, chatOptions, apiKey);
        request.setBaseUrl(trimTrailingSlash(resolveBaseUrl(chatOptions)));
        request.setApiPath(normalizeApiPath(resolveApiPath(chatOptions)));
        try {
            return apiResponseService.addTask(
                            request,
                            OpenAIChatCompletionResponseData.class,
                            true,
                            true)
                    .thenApply(OpenAIChatCompletionMapper::toResult);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "%s chat completion request failed.".formatted(getProvider()),
                    exception
            );
        }
    }

    /**
     * 读取必填 metadata 文本。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 非空字段值
     */
    private static String requireMetadata(Map<String, Object> metadata, String key) {
        return metadataString(metadata, key)
                .orElseThrow(() -> new IllegalStateException(
                        "Chat Completions metadata '%s' is required.".formatted(key)));
    }

    /**
     * 读取可选 metadata 文本。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 字符串 Optional
     */
    private static Optional<String> metadataString(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return Optional.empty();
        }
        var value = metadata.get(key).toString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * 移除 base URL 末尾斜杠。
     *
     * @param value base URL
     * @return 规范化 base URL
     */
    private static String trimTrailingSlash(String value) {
        var result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 确保 API path 以斜杠开头。
     *
     * @param value API path
     * @return 规范化 API path
     */
    private static String normalizeApiPath(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }
}
