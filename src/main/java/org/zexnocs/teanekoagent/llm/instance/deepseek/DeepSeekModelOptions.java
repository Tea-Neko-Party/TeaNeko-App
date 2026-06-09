package org.zexnocs.teanekoagent.llm.instance.deepseek;

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
 * DeepSeek 对话补全模型的默认调用参数。
 * <br>该类在通用 {@link LLMModelOptions} 基础上补充 DeepSeek create chat completion API 的专用参数。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class DeepSeekModelOptions extends LLMModelOptions {
    /**
     * DeepSeek 官方 API base URL。
     * <br>该值固定由 DeepSeek 适配器使用，不要求写入 file config。
     */
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /**
     * DeepSeek 对话补全 API path。
     */
    public static final String DEFAULT_API_PATH = "/chat/completions";

    /**
     * DeepSeek V4 Flash 模型名称。
     */
    public static final String MODEL_V4_FLASH = "deepseek-v4-flash";

    /**
     * DeepSeek V4 Pro 模型名称。
     */
    public static final String MODEL_V4_PRO = "deepseek-v4-pro";

    /**
     * DeepSeek 默认模型名称。
     */
    public static final String DEFAULT_MODEL = MODEL_V4_FLASH;

    /**
     * file config 中用于覆盖 API path 的 metadata key。
     */
    public static final String API_PATH_METADATA = "api";

    /**
     * metadata 中记录固定 base URL 的 key。
     */
    public static final String BASE_URL_METADATA = "baseUrl";

    /**
     * metadata 中记录 thinking.reasoning_effort 的 key。
     */
    public static final String REASONING_EFFORT_METADATA = "deepseek.reasoningEffort";

    /**
     * metadata 中记录 stream_options.include_usage 的 key。
     */
    public static final String STREAM_INCLUDE_USAGE_METADATA = "deepseek.streamIncludeUsage";

    /**
     * metadata 中记录 top_logprobs 的 key。
     */
    public static final String TOP_LOGPROBS_METADATA = "deepseek.topLogprobs";

    /**
     * metadata 中记录 user_id 的 key。
     */
    public static final String USER_ID_METADATA = "deepseek.userId";

    /**
     * DeepSeek 对话补全 API path。
     */
    @Nullable
    private String apiPath = DEFAULT_API_PATH;

    /**
     * DeepSeek reasoning effort 配置。
     * <br>该值会写入请求体中的 {@code thinking.reasoning_effort}。
     */
    @Nullable
    private String reasoningEffort;

    /**
     * 是否在流式响应的 stream_options 中包含 usage。
     */
    @Nullable
    private Boolean streamIncludeUsage;

    /**
     * 当 {@code logprobs} 开启时返回的候选 token 对数概率数量。
     */
    @Nullable
    private Integer topLogprobs;

    /**
     * DeepSeek 请求体中的 user_id。
     */
    @Nullable
    private String userId;

    /**
     * 创建 DeepSeek 模型的代码默认 options。
     *
     * @return DeepSeek 默认 options
     */
    public static DeepSeekModelOptions defaults() {
        var options = new DeepSeekModelOptions();
        options.setProvider(DeepSeekChatModel.PROVIDER);
        options.setModel(DEFAULT_MODEL);
        options.setResponseFormat(LLMResponseFormat.TEXT);
        options.setApiPath(DEFAULT_API_PATH);
        return options;
    }

    /**
     * 将任意 options 复制为 DeepSeek 专用 options。
     *
     * @param options 待复制的 options；为 {@code null} 时返回空 DeepSeek options
     * @return DeepSeek 专用 options
     */
    public static DeepSeekModelOptions copyOf(@Nullable ILLMModelOptions options) {
        var result = new DeepSeekModelOptions();
        copyCommonOptions(result, options);
        copyDeepSeekOptions(result, options);
        return result;
    }

    /**
     * 合并当前 DeepSeek options 与覆盖 options。
     * <br>该方法会保留 {@link DeepSeekModelOptions} 类型，确保 file config 和 prompt options 合并后仍能读取 DeepSeek 私有参数。
     *
     * @param overrides 覆盖 options
     * @return 合并后的 DeepSeek options
     */
    @Override
    public DeepSeekModelOptions mergeWith(@Nullable ILLMModelOptions overrides) {
        var result = copyOf(this);
        if (overrides == null) {
            return result;
        }

        _findSomething(overrides, result);

        var metadata = new LinkedHashMap<String, Object>();
        result.findMetadata().ifPresent(metadata::putAll);
        overrides.findMetadata().ifPresent(metadata::putAll);
        result.setMetadata(metadata);
        copyDeepSeekOptions(result, overrides);
        return result;
    }

    private static void _findSomething(ILLMModelOptions overrides, DeepSeekModelOptions result) {
        overrides.findProvider().ifPresent(result::setProvider);
        overrides.findModel().ifPresent(result::setModel);
        overrides.findThinking().ifPresent(result::setThinking);
        overrides.findMaxTokens().ifPresent(result::setMaxTokens);
        overrides.findFrequencyPenalty().ifPresent(result::setFrequencyPenalty);
        overrides.findTemperature().ifPresent(result::setTemperature);
        overrides.findTopP().ifPresent(result::setTopP);
        overrides.findPresencePenalty().ifPresent(result::setPresencePenalty);
        overrides.findResponseFormat().ifPresent(result::setResponseFormat);
        overrides.findStopWords().ifPresent(result::setStopWords);
        overrides.findStream().ifPresent(result::setStream);
        overrides.findTools().ifPresent(result::setTools);
        overrides.findToolChoice().ifPresent(result::setToolChoice);
        overrides.findLogprobs().ifPresent(result::setLogprobs);
    }

    /**
     * 获取 API path。
     *
     * @return API path Optional
     */
    public Optional<String> findApiPath() {
        return Optional.ofNullable(normalizeBlank(apiPath));
    }

    /**
     * 获取 reasoning effort。
     *
     * @return reasoning effort Optional
     */
    public Optional<String> findReasoningEffort() {
        return Optional.ofNullable(normalizeBlank(reasoningEffort));
    }

    /**
     * 获取 stream_options.include_usage。
     *
     * @return include usage Optional
     */
    public Optional<Boolean> findStreamIncludeUsage() {
        return Optional.ofNullable(streamIncludeUsage);
    }

    /**
     * 获取 top_logprobs。
     *
     * @return top_logprobs Optional
     */
    public Optional<Integer> findTopLogprobs() {
        return Optional.ofNullable(topLogprobs);
    }

    /**
     * 获取 user_id。
     *
     * @return user_id Optional
     */
    public Optional<String> findUserId() {
        return Optional.ofNullable(normalizeBlank(userId));
    }

    /**
     * 获取包含 DeepSeek 专用参数的 metadata。
     *
     * @return metadata
     */
    @Override
    public Map<String, Object> getMetadata() {
        var metadata = new LinkedHashMap<String, Object>();
        var superMetadata = super.getMetadata();
        if (superMetadata != null) {
            metadata.putAll(superMetadata);
        }
        metadata.put(BASE_URL_METADATA, DEFAULT_BASE_URL);
        putIfNotBlank(metadata, API_PATH_METADATA, apiPath);
        putIfNotBlank(metadata, REASONING_EFFORT_METADATA, reasoningEffort);
        putIfNotNull(metadata, STREAM_INCLUDE_USAGE_METADATA, streamIncludeUsage);
        putIfNotNull(metadata, TOP_LOGPROBS_METADATA, topLogprobs);
        putIfNotBlank(metadata, USER_ID_METADATA, userId);
        return new LinkedHashMap<>(metadata);
    }

    /**
     * 查找包含 DeepSeek 专用参数的 metadata。
     *
     * @return metadata Optional
     */
    @Override
    public Optional<Map<String, Object>> findMetadata() {
        return Optional.of(getMetadata());
    }

    /**
     * 复制通用 options 字段。
     *
     * @param target 目标 DeepSeek options
     * @param source 源 options
     */
    private static void copyCommonOptions(DeepSeekModelOptions target, @Nullable ILLMModelOptions source) {
        if (source == null) {
            return;
        }
        _findSomething(source, target);
        source.findMetadata().ifPresent(metadata -> target.setMetadata(new LinkedHashMap<>(metadata)));
    }

    /**
     * 复制 DeepSeek 专用 options 字段。
     *
     * @param target 目标 DeepSeek options
     * @param source 源 options
     */
    private static void copyDeepSeekOptions(DeepSeekModelOptions target, @Nullable ILLMModelOptions source) {
        var metadata = source == null
                ? Map.<String, Object>of()
                : source.findMetadata().orElse(Map.of());
        if (source instanceof DeepSeekModelOptions deepSeekOptions) {
            target.setApiPath(firstPresentText(deepSeekOptions.getApiPath(), metadataText(metadata, API_PATH_METADATA), DEFAULT_API_PATH));
            target.setReasoningEffort(firstPresentText(deepSeekOptions.getReasoningEffort(), metadataText(metadata, REASONING_EFFORT_METADATA), null));
            target.setStreamIncludeUsage(deepSeekOptions.getStreamIncludeUsage() == null
                    ? metadataBoolean(metadata, STREAM_INCLUDE_USAGE_METADATA).orElse(null)
                    : deepSeekOptions.getStreamIncludeUsage());
            target.setTopLogprobs(deepSeekOptions.getTopLogprobs() == null
                    ? metadataInteger(metadata, TOP_LOGPROBS_METADATA).orElse(null)
                    : deepSeekOptions.getTopLogprobs());
            target.setUserId(firstPresentText(deepSeekOptions.getUserId(), metadataText(metadata, USER_ID_METADATA), null));
            return;
        }
        target.setApiPath(metadataString(metadata, API_PATH_METADATA)
                .orElse(target.findApiPath().orElse(DEFAULT_API_PATH)));
        target.setReasoningEffort(metadataString(metadata, REASONING_EFFORT_METADATA)
                .orElse(target.findReasoningEffort().orElse(null)));
        target.setStreamIncludeUsage(metadataBoolean(metadata, STREAM_INCLUDE_USAGE_METADATA)
                .orElse(target.findStreamIncludeUsage().orElse(null)));
        target.setTopLogprobs(metadataInteger(metadata, TOP_LOGPROBS_METADATA)
                .orElse(target.findTopLogprobs().orElse(null)));
        target.setUserId(metadataString(metadata, USER_ID_METADATA)
                .orElse(target.findUserId().orElse(null)));
    }

    /**
     * 读取 metadata 中的字符串值。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 字符串 Optional
     */
    private static Optional<String> metadataString(Map<String, Object> metadata, String key) {
        var value = metadata.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(normalizeBlank(value.toString()));
    }

    /**
     * 读取 metadata 中的字符串值。
     *
     * @param metadata metadata
     * @param key 字段名
     * @return 字符串值；没有值或为空白时返回 {@code null}
     */
    @Nullable
    private static String metadataText(Map<String, Object> metadata, String key) {
        return metadataString(metadata, key).orElse(null);
    }

    /**
     * 读取 metadata 中的布尔值。
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
        if (value == null) {
            return Optional.empty();
        }
        var text = normalizeBlank(value.toString());
        return text == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(text));
    }

    /**
     * 读取 metadata 中的整数值。
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
        if (value == null) {
            return Optional.empty();
        }
        try {
            var text = normalizeBlank(value.toString());
            return text == null ? Optional.empty() : Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 返回第一个非空字符串。
     *
     * @param primary 优先值
     * @param fallback 回退值
     * @param defaultValue 默认值
     * @return 第一个非空字符串
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
     * 将空白字符串规范化为 {@code null}。
     *
     * @param value 字符串
     * @return 规范化后的字符串
     */
    @Nullable
    private static String normalizeBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 写入非空字符串字段。
     *
     * @param metadata metadata
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfNotBlank(Map<String, Object> metadata, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    /**
     * 写入非空字段。
     *
     * @param metadata metadata
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfNotNull(Map<String, Object> metadata, String key, @Nullable Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
