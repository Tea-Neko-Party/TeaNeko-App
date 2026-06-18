package org.zexnocs.teanekoagent_old.llm.instance.openai.responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.LLMResponseFormat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI Responses API 的模型调用参数。
 * <br>在通用 {@link LLMModelOptions} 基础上补充 reasoning、输出 verbosity、缓存和请求归属等参数。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class OpenAIModelOptions extends LLMModelOptions {
    /** OpenAI 官方 API base URL。 */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /** OpenAI Responses API path。 */
    public static final String DEFAULT_API_PATH = "/responses";

    /** OpenAI 当前默认模型。 */
    public static final String DEFAULT_MODEL = "gpt-5.5";

    /** metadata 中的 API path 字段名。 */
    public static final String API_PATH_METADATA = "api";

    /** metadata 中的 base URL 字段名。 */
    public static final String BASE_URL_METADATA = "baseUrl";

    /** metadata 中的 organization 字段名。 */
    public static final String ORGANIZATION_METADATA = "openai.organization";

    /** metadata 中的 project 字段名。 */
    public static final String PROJECT_METADATA = "openai.project";

    /** metadata 中的 reasoning effort 字段名。 */
    public static final String REASONING_EFFORT_METADATA = "openai.reasoningEffort";

    /** metadata 中的 reasoning summary 字段名。 */
    public static final String REASONING_SUMMARY_METADATA = "openai.reasoningSummary";

    /** metadata 中的输出 verbosity 字段名。 */
    public static final String VERBOSITY_METADATA = "openai.verbosity";

    /** metadata 中的响应存储开关字段名。 */
    public static final String STORE_METADATA = "openai.store";

    /** metadata 中的并行工具调用字段名。 */
    public static final String PARALLEL_TOOL_CALLS_METADATA = "openai.parallelToolCalls";

    /** metadata 中的 prompt cache key 字段名。 */
    public static final String PROMPT_CACHE_KEY_METADATA = "openai.promptCacheKey";

    /** metadata 中的安全标识字段名。 */
    public static final String SAFETY_IDENTIFIER_METADATA = "openai.safetyIdentifier";

    /** metadata 中的 service tier 字段名。 */
    public static final String SERVICE_TIER_METADATA = "openai.serviceTier";

    /** metadata 中的截断策略字段名。 */
    public static final String TRUNCATION_METADATA = "openai.truncation";

    /** metadata 中的 top logprobs 字段名。 */
    public static final String TOP_LOGPROBS_METADATA = "openai.topLogprobs";

    /** metadata 中的 OpenAI 请求 metadata 字段名。 */
    public static final String REQUEST_METADATA = "openai.metadata";

    /** OpenAI API base URL。 */
    @Nullable
    private String baseUrl = DEFAULT_BASE_URL;

    /** OpenAI Responses API path。 */
    @Nullable
    private String apiPath = DEFAULT_API_PATH;

    /** OpenAI organization 请求头。 */
    @Nullable
    private String organization;

    /** OpenAI project 请求头。 */
    @Nullable
    private String project;

    /** Responses API reasoning effort。 */
    @Nullable
    private String reasoningEffort;

    /** Responses API reasoning summary。 */
    @Nullable
    private String reasoningSummary;

    /** 输出文本 verbosity。 */
    @Nullable
    private String verbosity;

    /** 是否允许 OpenAI 存储响应。 */
    @Nullable
    private Boolean store;

    /** 是否允许并行 function calls。 */
    @Nullable
    private Boolean parallelToolCalls;

    /** Prompt 缓存键。 */
    @Nullable
    private String promptCacheKey;

    /** 用于安全检测的稳定用户标识。 */
    @Nullable
    private String safetyIdentifier;

    /** OpenAI 服务层级。 */
    @Nullable
    private String serviceTier;

    /** 上下文截断策略。 */
    @Nullable
    private String truncation;

    /** 返回每个 token 的候选 logprobs 数量。 */
    @Nullable
    private Integer topLogprobs;

    /** 写入 OpenAI 请求体的 metadata。 */
    private Map<String, String> requestMetadata = new LinkedHashMap<>();

    /**
     * 创建 OpenAI 模型代码自带的 base options。
     *
     * @return OpenAI base options
     */
    public static OpenAIModelOptions baseOptions() {
        var options = new OpenAIModelOptions();
        options.setProvider(OpenAIResponsesModel.PROVIDER);
        options.setModel(DEFAULT_MODEL);
        options.setResponseFormat(LLMResponseFormat.TEXT);
        return options;
    }

    /**
     * 将任意模型参数复制为 OpenAI 专用参数。
     *
     * @param options 源参数
     * @return OpenAI 专用参数
     */
    public static OpenAIModelOptions copyOf(@Nullable ILLMModelOptions options) {
        var result = new OpenAIModelOptions();
        copyCommonOptions(result, options);
        copyOpenAIOptions(result, options);
        return result;
    }

    /**
     * 合并 OpenAI base options 与调用覆盖参数，并保留专用参数类型。
     *
     * @param overrides 覆盖参数
     * @return 合并后的 OpenAI 参数
     */
    @Override
    public OpenAIModelOptions mergeWith(@Nullable ILLMModelOptions overrides) {
        var result = copyOf(this);
        if (overrides == null) {
            return result;
        }
        copyCommonValues(result, overrides);
        var metadata = new LinkedHashMap<String, Object>();
        result.findMetadata().ifPresent(metadata::putAll);
        overrides.findMetadata().ifPresent(metadata::putAll);
        result.setMetadata(metadata);
        copyOpenAIOptions(result, overrides);
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

    /** @return reasoning effort Optional */
    public Optional<String> findReasoningEffort() {
        return Optional.ofNullable(normalizeBlank(reasoningEffort));
    }

    /** @return reasoning summary Optional */
    public Optional<String> findReasoningSummary() {
        return Optional.ofNullable(normalizeBlank(reasoningSummary));
    }

    /** @return verbosity Optional */
    public Optional<String> findVerbosity() {
        return Optional.ofNullable(normalizeBlank(verbosity));
    }

    /** @return store Optional */
    public Optional<Boolean> findStore() {
        return Optional.ofNullable(store);
    }

    /** @return parallel tool calls Optional */
    public Optional<Boolean> findParallelToolCalls() {
        return Optional.ofNullable(parallelToolCalls);
    }

    /** @return prompt cache key Optional */
    public Optional<String> findPromptCacheKey() {
        return Optional.ofNullable(normalizeBlank(promptCacheKey));
    }

    /** @return safety identifier Optional */
    public Optional<String> findSafetyIdentifier() {
        return Optional.ofNullable(normalizeBlank(safetyIdentifier));
    }

    /** @return service tier Optional */
    public Optional<String> findServiceTier() {
        return Optional.ofNullable(normalizeBlank(serviceTier));
    }

    /** @return truncation Optional */
    public Optional<String> findTruncation() {
        return Optional.ofNullable(normalizeBlank(truncation));
    }

    /** @return top logprobs Optional */
    public Optional<Integer> findTopLogprobs() {
        return Optional.ofNullable(topLogprobs);
    }

    /**
     * 返回包含 OpenAI 专用参数的 metadata。
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
        putIfNotBlank(metadata, REASONING_EFFORT_METADATA, reasoningEffort);
        putIfNotBlank(metadata, REASONING_SUMMARY_METADATA, reasoningSummary);
        putIfNotBlank(metadata, VERBOSITY_METADATA, verbosity);
        putIfNotNull(metadata, STORE_METADATA, store);
        putIfNotNull(metadata, PARALLEL_TOOL_CALLS_METADATA, parallelToolCalls);
        putIfNotBlank(metadata, PROMPT_CACHE_KEY_METADATA, promptCacheKey);
        putIfNotBlank(metadata, SAFETY_IDENTIFIER_METADATA, safetyIdentifier);
        putIfNotBlank(metadata, SERVICE_TIER_METADATA, serviceTier);
        putIfNotBlank(metadata, TRUNCATION_METADATA, truncation);
        putIfNotNull(metadata, TOP_LOGPROBS_METADATA, topLogprobs);
        if (requestMetadata != null && !requestMetadata.isEmpty()) {
            metadata.put(REQUEST_METADATA, new LinkedHashMap<>(requestMetadata));
        }
        return metadata;
    }

    /** @return metadata Optional */
    @Override
    public Optional<Map<String, Object>> findMetadata() {
        return Optional.of(getMetadata());
    }

    /**
     * 复制通用 options 和 metadata。
     *
     * @param target 目标 OpenAI options
     * @param source 源 options
     */
    private static void copyCommonOptions(OpenAIModelOptions target, @Nullable ILLMModelOptions source) {
        if (source == null) {
            return;
        }
        copyCommonValues(target, source);
        source.findMetadata().ifPresent(metadata -> target.setMetadata(new LinkedHashMap<>(metadata)));
    }

    /**
     * 复制通用 options 字段。
     *
     * @param target 目标 OpenAI options
     * @param source 源 options
     */
    private static void copyCommonValues(OpenAIModelOptions target, ILLMModelOptions source) {
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
     * 从专用字段或 metadata 复制 OpenAI 参数。
     *
     * @param target 目标 OpenAI options
     * @param source 源 options
     */
    private static void copyOpenAIOptions(OpenAIModelOptions target, @Nullable ILLMModelOptions source) {
        var metadata = source == null ? Map.<String, Object>of() : source.findMetadata().orElse(Map.of());
        if (source instanceof OpenAIModelOptions openAIOptions) {
            target.setBaseUrl(firstPresentText(openAIOptions.getBaseUrl(), metadataText(metadata, BASE_URL_METADATA), target.getBaseUrl()));
            target.setApiPath(firstPresentText(openAIOptions.getApiPath(), metadataText(metadata, API_PATH_METADATA), target.getApiPath()));
            target.setOrganization(firstPresentText(openAIOptions.getOrganization(), metadataText(metadata, ORGANIZATION_METADATA), target.getOrganization()));
            target.setProject(firstPresentText(openAIOptions.getProject(), metadataText(metadata, PROJECT_METADATA), target.getProject()));
            target.setReasoningEffort(firstPresentText(openAIOptions.getReasoningEffort(), metadataText(metadata, REASONING_EFFORT_METADATA), target.getReasoningEffort()));
            target.setReasoningSummary(firstPresentText(openAIOptions.getReasoningSummary(), metadataText(metadata, REASONING_SUMMARY_METADATA), target.getReasoningSummary()));
            target.setVerbosity(firstPresentText(openAIOptions.getVerbosity(), metadataText(metadata, VERBOSITY_METADATA), target.getVerbosity()));
            target.setStore(firstPresentValue(openAIOptions.getStore(), metadataBoolean(metadata, STORE_METADATA).orElse(null), target.getStore()));
            target.setParallelToolCalls(firstPresentValue(openAIOptions.getParallelToolCalls(), metadataBoolean(metadata, PARALLEL_TOOL_CALLS_METADATA).orElse(null), target.getParallelToolCalls()));
            target.setPromptCacheKey(firstPresentText(openAIOptions.getPromptCacheKey(), metadataText(metadata, PROMPT_CACHE_KEY_METADATA), target.getPromptCacheKey()));
            target.setSafetyIdentifier(firstPresentText(openAIOptions.getSafetyIdentifier(), metadataText(metadata, SAFETY_IDENTIFIER_METADATA), target.getSafetyIdentifier()));
            target.setServiceTier(firstPresentText(openAIOptions.getServiceTier(), metadataText(metadata, SERVICE_TIER_METADATA), target.getServiceTier()));
            target.setTruncation(firstPresentText(openAIOptions.getTruncation(), metadataText(metadata, TRUNCATION_METADATA), target.getTruncation()));
            target.setTopLogprobs(firstPresentValue(openAIOptions.getTopLogprobs(), metadataInteger(metadata, TOP_LOGPROBS_METADATA).orElse(null), target.getTopLogprobs()));
            target.setRequestMetadata(mergeRequestMetadata(target.getRequestMetadata(), openAIOptions.getRequestMetadata(), metadataMap(metadata, REQUEST_METADATA)));
            return;
        }
        target.setBaseUrl(firstPresentText(metadataText(metadata, BASE_URL_METADATA), target.getBaseUrl(), DEFAULT_BASE_URL));
        target.setApiPath(firstPresentText(metadataText(metadata, API_PATH_METADATA), target.getApiPath(), DEFAULT_API_PATH));
        target.setOrganization(firstPresentText(metadataText(metadata, ORGANIZATION_METADATA), target.getOrganization(), null));
        target.setProject(firstPresentText(metadataText(metadata, PROJECT_METADATA), target.getProject(), null));
        target.setReasoningEffort(firstPresentText(metadataText(metadata, REASONING_EFFORT_METADATA), target.getReasoningEffort(), null));
        target.setReasoningSummary(firstPresentText(metadataText(metadata, REASONING_SUMMARY_METADATA), target.getReasoningSummary(), null));
        target.setVerbosity(firstPresentText(metadataText(metadata, VERBOSITY_METADATA), target.getVerbosity(), null));
        target.setStore(metadataBoolean(metadata, STORE_METADATA).orElse(target.getStore()));
        target.setParallelToolCalls(metadataBoolean(metadata, PARALLEL_TOOL_CALLS_METADATA).orElse(target.getParallelToolCalls()));
        target.setPromptCacheKey(firstPresentText(metadataText(metadata, PROMPT_CACHE_KEY_METADATA), target.getPromptCacheKey(), null));
        target.setSafetyIdentifier(firstPresentText(metadataText(metadata, SAFETY_IDENTIFIER_METADATA), target.getSafetyIdentifier(), null));
        target.setServiceTier(firstPresentText(metadataText(metadata, SERVICE_TIER_METADATA), target.getServiceTier(), null));
        target.setTruncation(firstPresentText(metadataText(metadata, TRUNCATION_METADATA), target.getTruncation(), null));
        target.setTopLogprobs(metadataInteger(metadata, TOP_LOGPROBS_METADATA).orElse(target.getTopLogprobs()));
        target.setRequestMetadata(mergeRequestMetadata(target.getRequestMetadata(), metadataMap(metadata, REQUEST_METADATA)));
    }

    /**
     * 按顺序合并请求 metadata，后面的 map 覆盖前面的同名字段。
     *
     * @param sources metadata 来源
     * @return 合并后的 metadata
     */
    @SafeVarargs
    private static Map<String, String> mergeRequestMetadata(Map<String, String>... sources) {
        var result = new LinkedHashMap<String, String>();
        for (var source : sources) {
            if (source != null) {
                result.putAll(source);
            }
        }
        return result;
    }

    /**
     * 读取 metadata 中的字符串 map。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 字符串 map
     */
    private static Map<String, String> metadataMap(Map<String, Object> metadata, String key) {
        var value = metadata.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, String>();
        map.forEach((mapKey, mapValue) -> {
            if (mapKey != null && mapValue != null) {
                result.put(mapKey.toString(), mapValue.toString());
            }
        });
        return result;
    }

    /**
     * 读取 metadata 布尔值。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 布尔值 Optional
     */
    private static Optional<Boolean> metadataBoolean(Map<String, Object> metadata, String key) {
        var value = metadata.get(key);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        var text = value == null ? null : normalizeBlank(value.toString());
        return text == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(text));
    }

    /**
     * 读取 metadata 整数值。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 整数 Optional
     */
    private static Optional<Integer> metadataInteger(Map<String, Object> metadata, String key) {
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
     * 读取 metadata 字符串值。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 非空白字符串或 {@code null}
     */
    @Nullable
    private static String metadataText(Map<String, Object> metadata, String key) {
        var value = metadata.get(key);
        return value == null ? null : normalizeBlank(value.toString());
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param primary 优先值
     * @param fallback 回退值
     * @param defaultValue 默认值
     * @return 第一个有效字符串
     */
    @Nullable
    private static String firstPresentText(@Nullable String primary,
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
    private static <T> T firstPresentValue(@Nullable T... values) {
        for (var value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 将空白字符串规范化为 {@code null}。
     *
     * @param value 字符串
     * @return 非空白字符串或 {@code null}
     */
    @Nullable
    private static String normalizeBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 向 map 写入非空白字符串。
     *
     * @param target 目标 map
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfNotBlank(Map<String, Object> target, String key, @Nullable String value) {
        var normalized = normalizeBlank(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    /**
     * 向 map 写入非空值。
     *
     * @param target 目标 map
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfNotNull(Map<String, Object> target, String key, @Nullable Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
