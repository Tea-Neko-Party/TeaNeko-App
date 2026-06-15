package org.zexnocs.teanekoagent.llm.instance.openai.completions;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI Chat Completions 兼容接口的通用模型参数。
 * <br>该类同时作为第三方 OpenAI 兼容服务的扩展基类，供应商专用字段可通过 {@code body.} 和
 * {@code header.} metadata 前缀透传。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class OpenAIChatCompletionModelOptions extends LLMModelOptions {
    /** OpenAI 官方 API base URL。 */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /** Chat Completions API path。 */
    public static final String DEFAULT_API_PATH = "/chat/completions";

    /** OpenAI Chat Completions 默认模型。 */
    public static final String DEFAULT_MODEL = "gpt-5.5";

    /** metadata 中的 base URL 字段名。 */
    public static final String BASE_URL_METADATA = "baseUrl";

    /** metadata 中的 API path 字段名。 */
    public static final String API_PATH_METADATA = "api";

    /** metadata 中的 organization 字段名。 */
    public static final String ORGANIZATION_METADATA = "openaiChat.organization";

    /** metadata 中的 project 字段名。 */
    public static final String PROJECT_METADATA = "openaiChat.project";

    /** metadata 中的并行工具调用字段名。 */
    public static final String PARALLEL_TOOL_CALLS_METADATA = "openaiChat.parallelToolCalls";

    /** metadata 中的 top logprobs 字段名。 */
    public static final String TOP_LOGPROBS_METADATA = "openaiChat.topLogprobs";

    /** metadata 中写入额外请求体字段的前缀。 */
    public static final String EXTRA_BODY_PREFIX = "body.";

    /** metadata 中写入额外请求头的前缀。 */
    public static final String EXTRA_HEADER_PREFIX = "header.";

    /** Chat Completions 服务 base URL。 */
    @Nullable
    private String baseUrl = DEFAULT_BASE_URL;

    /** Chat Completions API path。 */
    @Nullable
    private String apiPath = DEFAULT_API_PATH;

    /** OpenAI organization 请求头。 */
    @Nullable
    private String organization;

    /** OpenAI project 请求头。 */
    @Nullable
    private String project;

    /** 是否允许并行 Function Tool 调用。 */
    @Nullable
    private Boolean parallelToolCalls;

    /** 返回每个 token 的候选 logprobs 数量。 */
    @Nullable
    private Integer topLogprobs;

    /**
     * 创建 OpenAI Chat Completions 模型代码自带的 base options。
     *
     * @return base options
     */
    public static OpenAIChatCompletionModelOptions baseOptions() {
        return baseOptions(OpenAIChatCompletionsModel.PROVIDER, DEFAULT_MODEL, DEFAULT_BASE_URL);
    }

    /**
     * 为兼容供应商创建模型代码自带的 base options。
     *
     * @param provider 模型适配器 ID
     * @param model 默认模型名称
     * @param baseUrl API base URL
     * @return base options
     */
    public static OpenAIChatCompletionModelOptions baseOptions(String provider,
                                                                String model,
                                                                String baseUrl) {
        var options = new OpenAIChatCompletionModelOptions();
        options.setProvider(provider);
        options.setModel(model);
        options.setBaseUrl(baseUrl);
        options.setApiPath(DEFAULT_API_PATH);
        options.setResponseFormat(LLMResponseFormat.TEXT);
        return options;
    }

    /**
     * 将任意模型参数复制为 Chat Completions 参数。
     *
     * @param options 源参数
     * @return Chat Completions 参数
     */
    public static OpenAIChatCompletionModelOptions copyOf(@Nullable ILLMModelOptions options) {
        var result = new OpenAIChatCompletionModelOptions();
        copyCommonOptions(result, options);
        copyChatCompletionOptions(result, options);
        return result;
    }

    /**
     * 合并 base options 和本次调用覆盖参数。
     *
     * @param overrides 覆盖参数
     * @return 合并后的 Chat Completions 参数
     */
    @Override
    public OpenAIChatCompletionModelOptions mergeWith(@Nullable ILLMModelOptions overrides) {
        var result = copyOf(this);
        if (overrides == null) {
            return result;
        }
        copyCommonValues(result, overrides);
        var metadata = new LinkedHashMap<String, Object>();
        result.findMetadata().ifPresent(metadata::putAll);
        overrides.findMetadata().ifPresent(metadata::putAll);
        result.setMetadata(metadata);
        copyChatCompletionOptions(result, overrides);
        return result;
    }

    /** @return base URL Optional */
    public Optional<String> findBaseUrl() {
        return Optional.ofNullable(normalizeBlank(baseUrl));
    }

    /** @return API path Optional */
    public Optional<String> findApiPath() {
        return Optional.ofNullable(normalizeBlank(apiPath));
    }

    /** @return organization Optional */
    public Optional<String> findOrganization() {
        return Optional.ofNullable(normalizeBlank(organization));
    }

    /** @return project Optional */
    public Optional<String> findProject() {
        return Optional.ofNullable(normalizeBlank(project));
    }

    /** @return parallel tool calls Optional */
    public Optional<Boolean> findParallelToolCalls() {
        return Optional.ofNullable(parallelToolCalls);
    }

    /** @return top logprobs Optional */
    public Optional<Integer> findTopLogprobs() {
        return Optional.ofNullable(topLogprobs);
    }

    /**
     * 获取包含 Chat Completions 扩展参数的 metadata。
     *
     * @return metadata 副本
     */
    @Override
    public Map<String, Object> getMetadata() {
        var metadata = new LinkedHashMap<String, Object>();
        var parentMetadata = super.getMetadata();
        if (parentMetadata != null) {
            metadata.putAll(parentMetadata);
        }
        putIfNotBlank(metadata, BASE_URL_METADATA, baseUrl);
        putIfNotBlank(metadata, API_PATH_METADATA, apiPath);
        putIfNotBlank(metadata, ORGANIZATION_METADATA, organization);
        putIfNotBlank(metadata, PROJECT_METADATA, project);
        putIfNotNull(metadata, PARALLEL_TOOL_CALLS_METADATA, parallelToolCalls);
        putIfNotNull(metadata, TOP_LOGPROBS_METADATA, topLogprobs);
        return metadata;
    }

    /** @return metadata Optional */
    @Override
    public Optional<Map<String, Object>> findMetadata() {
        return Optional.of(getMetadata());
    }

    /**
     * 复制通用参数和原始 metadata。
     *
     * @param target 目标参数
     * @param source 源参数
     */
    protected static void copyCommonOptions(OpenAIChatCompletionModelOptions target,
                                            @Nullable ILLMModelOptions source) {
        if (source == null) {
            return;
        }
        copyCommonValues(target, source);
        source.findMetadata().ifPresent(metadata -> target.setMetadata(new LinkedHashMap<>(metadata)));
    }

    /**
     * 复制通用模型字段。
     *
     * @param target 目标参数
     * @param source 源参数
     */
    protected static void copyCommonValues(OpenAIChatCompletionModelOptions target,
                                           ILLMModelOptions source) {
        source.findProvider().ifPresent(target::setProvider);
        source.findModel().ifPresent(target::setModel);
        source.findThinking().ifPresent(target::setThinking);
        source.findMaxTokens().ifPresent(target::setMaxTokens);
        source.findFrequencyPenalty().ifPresent(target::setFrequencyPenalty);
        source.findTemperature().ifPresent(target::setTemperature);
        source.findTopP().ifPresent(target::setTopP);
        source.findPresencePenalty().ifPresent(target::setPresencePenalty);
        source.findResponseFormat().ifPresent(target::setResponseFormat);
        source.findStopWords().ifPresent(target::setStopWords);
        source.findStream().ifPresent(target::setStream);
        source.findTools().ifPresent(target::setTools);
        source.findToolChoice().ifPresent(target::setToolChoice);
        source.findLogprobs().ifPresent(target::setLogprobs);
    }

    /**
     * 从专用字段或 metadata 复制 Chat Completions 参数。
     *
     * @param target 目标参数
     * @param source 源参数
     */
    protected static void copyChatCompletionOptions(OpenAIChatCompletionModelOptions target,
                                                    @Nullable ILLMModelOptions source) {
        var metadata = source == null ? Map.<String, Object>of() : source.findMetadata().orElse(Map.of());
        if (source instanceof OpenAIChatCompletionModelOptions chatOptions) {
            target.setBaseUrl(firstPresentText(chatOptions.getBaseUrl(), metadataText(metadata, BASE_URL_METADATA), target.getBaseUrl()));
            target.setApiPath(firstPresentText(chatOptions.getApiPath(), metadataText(metadata, API_PATH_METADATA), target.getApiPath()));
            target.setOrganization(firstPresentText(chatOptions.getOrganization(), metadataText(metadata, ORGANIZATION_METADATA), target.getOrganization()));
            target.setProject(firstPresentText(chatOptions.getProject(), metadataText(metadata, PROJECT_METADATA), target.getProject()));
            target.setParallelToolCalls(firstPresentValue(chatOptions.getParallelToolCalls(), metadataBoolean(metadata, PARALLEL_TOOL_CALLS_METADATA).orElse(null), target.getParallelToolCalls()));
            target.setTopLogprobs(firstPresentValue(chatOptions.getTopLogprobs(), metadataInteger(metadata, TOP_LOGPROBS_METADATA).orElse(null), target.getTopLogprobs()));
            return;
        }
        target.setBaseUrl(firstPresentText(metadataText(metadata, BASE_URL_METADATA), target.getBaseUrl(), DEFAULT_BASE_URL));
        target.setApiPath(firstPresentText(metadataText(metadata, API_PATH_METADATA), target.getApiPath(), DEFAULT_API_PATH));
        target.setOrganization(firstPresentText(metadataText(metadata, ORGANIZATION_METADATA), target.getOrganization(), null));
        target.setProject(firstPresentText(metadataText(metadata, PROJECT_METADATA), target.getProject(), null));
        target.setParallelToolCalls(metadataBoolean(metadata, PARALLEL_TOOL_CALLS_METADATA).orElse(target.getParallelToolCalls()));
        target.setTopLogprobs(metadataInteger(metadata, TOP_LOGPROBS_METADATA).orElse(target.getTopLogprobs()));
    }

    /**
     * 读取 metadata 字符串。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 非空白字符串或 {@code null}
     */
    @Nullable
    protected static String metadataText(Map<String, Object> metadata, String key) {
        var value = metadata.get(key);
        return value == null ? null : normalizeBlank(value.toString());
    }

    /**
     * 读取 metadata 布尔值。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 布尔值 Optional
     */
    protected static Optional<Boolean> metadataBoolean(Map<String, Object> metadata, String key) {
        var value = metadata.get(key);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        var text = value == null ? null : normalizeBlank(value.toString());
        return text == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(text));
    }

    /**
     * 读取 metadata 整数。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 整数 Optional
     */
    protected static Optional<Integer> metadataInteger(Map<String, Object> metadata, String key) {
        var value = metadata.get(key);
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        try {
            var text = value == null ? null : normalizeBlank(value.toString());
            return text == null ? Optional.empty() : Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 返回第一个非空白文本。
     *
     * @param primary 优先值
     * @param fallback 回退值
     * @param defaultValue 默认值
     * @return 解析后的文本
     */
    @Nullable
    protected static String firstPresentText(@Nullable String primary,
                                             @Nullable String fallback,
                                             @Nullable String defaultValue) {
        var primaryText = normalizeBlank(primary);
        if (primaryText != null) {
            return primaryText;
        }
        var fallbackText = normalizeBlank(fallback);
        return fallbackText == null ? defaultValue : fallbackText;
    }

    /**
     * 返回第一个非空值。
     *
     * @param values 候选值
     * @return 第一个非空值
     * @param <T> 值类型
     */
    @SafeVarargs
    @Nullable
    protected static <T> T firstPresentValue(@Nullable T... values) {
        for (var value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 将空白文本规范化为 {@code null}。
     *
     * @param value 文本
     * @return 非空白文本或 {@code null}
     */
    @Nullable
    protected static String normalizeBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 写入非空白字符串。
     *
     * @param target 目标 map
     * @param key 字段名
     * @param value 字段值
     */
    protected static void putIfNotBlank(Map<String, Object> target, String key, @Nullable String value) {
        var normalized = normalizeBlank(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    /**
     * 写入非空值。
     *
     * @param target 目标 map
     * @param key 字段名
     * @param value 字段值
     */
    protected static void putIfNotNull(Map<String, Object> target, String key, @Nullable Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
