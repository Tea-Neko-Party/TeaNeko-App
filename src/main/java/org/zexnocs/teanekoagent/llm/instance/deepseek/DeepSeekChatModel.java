package org.zexnocs.teanekoagent.llm.instance.deepseek;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.AbstractLLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.Map;
import java.util.Optional;

/**
 * DeepSeek 对话补全模型适配器。
 * <br>注册 ID 为 {@code deepseek}，默认模型名称为 {@code deepseek-v4-flash}。
 * <br>API key、base URL 等访问参数应通过 LLM 文件配置或数据库写入 {@link LLMModelOptions#getMetadata()}。
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 */
@Service
public class DeepSeekChatModel extends AbstractLLMModel {
    /**
     * DeepSeek 模型适配器注册 ID。
     */
    public static final String PROVIDER = "deepseek";

    /**
     * DeepSeek 当前推荐的默认对话模型。
     */
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";

    /**
     * DeepSeek 对话补全接口路径。
     */
    public static final String DEFAULT_API = "/chat/completions";

    /**
     * API key 在 metadata 中的字段名。
     */
    public static final String API_KEY_METADATA = "apiKey";

    /**
     * base URL 在 metadata 中的字段名。
     */
    public static final String BASE_URL_METADATA = "baseUrl";

    /**
     * API path 在 metadata 中的字段名。
     */
    public static final String API_METADATA = "api";

    /**
     * API 响应服务。
     * <br>DeepSeek 的 HTTP 请求统一经由 TeaNeko Core 的 api_response 模块发送。
     */
    private final IAPIResponseService apiResponseService;

    /**
     * 创建 DeepSeek 对话补全模型适配器。
     *
     * @param apiResponseService API 响应服务
     */
    public DeepSeekChatModel(IAPIResponseService apiResponseService) {
        super(PROVIDER, DEFAULT_MODEL, LLMModelOptions.builder()
                .provider(PROVIDER)
                .model(DEFAULT_MODEL)
                .responseFormat(LLMResponseFormat.TEXT)
                .build());
        this.apiResponseService = apiResponseService;
    }

    /**
     * 判断 DeepSeek 适配器是否支持给定 options。
     * <br>当前实现支持非流式对话补全、文本输出和 JSON Object 输出。
     *
     * @param options 模型调用参数
     * @return 如果支持该调用参数则返回 {@code true}
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
        return providerSupported && responseFormatSupported && streamSupported;
    }

    /**
     * 调用 DeepSeek 对话补全 API。
     *
     * @param prompt 本次调用的提示词与消息
     * @param options 已完成合并的模型调用选项
     * @return DeepSeek 响应转换后的统一 LLM 结果
     */
    @Override
    protected ITaskResult<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options) {
        var metadata = options.getMetadata();
        var apiKey = requireMetadata(metadata, API_KEY_METADATA);
        var baseUrl = requireMetadata(metadata, BASE_URL_METADATA);
        var api = metadataString(metadata, API_METADATA).orElse(DEFAULT_API);
        var request = DeepSeekChatCompletionMapper.toRequest(prompt,
                options,
                trimTrailingSlash(baseUrl),
                normalizeApiPath(api),
                apiKey);
        DeepSeekChatCompletionResponseData response;
        try {
            var responseFuture = apiResponseService.addTask(
                    request,
                    DeepSeekChatCompletionResponseData.class,
                    true,
                    true);
            response = responseFuture.finish().join();
        } catch (Exception exception) {
            throw new IllegalStateException("DeepSeek chat completion request failed.", exception);
        }
        if (response == null) {
            throw new IllegalStateException("DeepSeek response body is empty.");
        }
        return new TaskResult<>(true, DeepSeekChatCompletionMapper.toResult(response));
    }

    /**
     * 读取必填 metadata 字段。
     *
     * @param metadata metadata 映射
     * @param key 字段名
     * @return 字段值
     * @throws IllegalStateException 当字段为空时抛出
     */
    private static String requireMetadata(Map<String, Object> metadata, String key) {
        return metadataString(metadata, key)
                .orElseThrow(() -> new IllegalStateException("DeepSeek metadata '%s' is required.".formatted(key)));
    }

    /**
     * 从 metadata 中读取字符串字段。
     *
     * @param metadata metadata 映射
     * @param key 字段名
     * @return 字符串字段值
     */
    private static Optional<String> metadataString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return Optional.empty();
        }
        var value = metadata.get(key);
        if (value == null) {
            return Optional.empty();
        }
        var text = value.toString();
        if (text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    /**
     * 去除 base URL 末尾的斜杠。
     *
     * @param value base URL
     * @return 规范化后的 base URL
     */
    private static String trimTrailingSlash(String value) {
        var result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 规范化 API path。
     *
     * @param value API path
     * @return 以斜杠开头的 API path
     */
    private static String normalizeApiPath(String value) {
        if (value.startsWith("/")) {
            return value;
        }
        return "/" + value;
    }
}
