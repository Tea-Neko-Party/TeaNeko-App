package org.zexnocs.teanekoagent.llm.instance.openai.responses;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.AbstractLLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI Responses API 模型适配器。
 * <br>注册 ID 为 {@code openai}，默认使用 {@code gpt-5.5} 和非流式 {@code /responses} 调用。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@LLMModel(id = OpenAIResponsesModel.PROVIDER)
public class OpenAIResponsesModel extends AbstractLLMModel {
    /** OpenAI 模型适配器注册 ID。 */
    public static final String PROVIDER = "openai";

    /** OpenAI 默认模型名称。 */
    public static final String DEFAULT_MODEL = OpenAIModelOptions.DEFAULT_MODEL;

    /** API key 在 metadata 中的字段名。 */
    public static final String API_KEY_METADATA = "apiKey";

    /** API 响应服务。 */
    private final IAPIResponseService apiResponseService;

    /**
     * 创建 OpenAI Responses 模型适配器。
     *
     * @param apiResponseService API 响应服务
     */
    public OpenAIResponsesModel(IAPIResponseService apiResponseService) {
        super(PROVIDER, DEFAULT_MODEL, OpenAIModelOptions.baseOptions());
        this.apiResponseService = apiResponseService;
    }

    /**
     * 判断 OpenAI Responses 适配器是否支持给定参数。
     * <br>当前实现仅处理非流式响应；Responses API 未声明的 stop、frequency penalty 和 presence penalty 会被拒绝。
     *
     * @param options 模型参数
     * @return 参数能够完整映射时返回 {@code true}
     */
    @Override
    public boolean supports(ILLMModelOptions options) {
        var standardOptions = LLMModelOptions.copyOf(options);
        var providerSupported = standardOptions.findProvider()
                .map(PROVIDER::equals)
                .orElse(true);
        var responseFormatSupported = standardOptions.findResponseFormat()
                .map(format -> format == LLMResponseFormat.TEXT || format == LLMResponseFormat.JSON)
                .orElse(true);
        var streamSupported = standardOptions.findStream()
                .map(stream -> !stream)
                .orElse(true);
        var stopSupported = standardOptions.findStopWords()
                .map(List::isEmpty)
                .orElse(true);
        return providerSupported
                && responseFormatSupported
                && streamSupported
                && stopSupported
                && standardOptions.findFrequencyPenalty().isEmpty()
                && standardOptions.findPresencePenalty().isEmpty();
    }

    /**
     * 调用 OpenAI Responses API。
     *
     * @param prompt 本次调用的 Prompt
     * @param options 已完成合并的模型参数
     * @return OpenAI 响应转换后的统一 LLM Result Future
     */
    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options) {
        var openAIOptions = OpenAIModelOptions.copyOf(options);
        var apiKey = requireMetadata(openAIOptions.getMetadata(), API_KEY_METADATA);
        var request = OpenAIResponsesMapper.toRequest(prompt, openAIOptions, apiKey);
        request.setBaseUrl(trimTrailingSlash(request.getBaseUrl()));
        request.setApiPath(normalizeApiPath(request.getApiPath()));
        try {
            return apiResponseService.addTask(
                            request,
                            OpenAIResponsesResponseData.class,
                            true,
                            true)
                    .thenApply(OpenAIResponsesMapper::toResult);
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI Responses API request failed.", exception);
        }
    }

    /**
     * 读取必填 metadata 字符串。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 非空字段值
     * @throws IllegalStateException 当字段缺失时抛出
     */
    private static String requireMetadata(Map<String, Object> metadata, String key) {
        return metadataString(metadata, key)
                .orElseThrow(() -> new IllegalStateException(
                        "OpenAI metadata '%s' is required.".formatted(key)));
    }

    /**
     * 读取可选 metadata 字符串。
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
