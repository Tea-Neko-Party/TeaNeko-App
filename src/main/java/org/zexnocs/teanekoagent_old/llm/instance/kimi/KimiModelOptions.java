package org.zexnocs.teanekoagent_old.llm.instance.kimi;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent_old.llm.instance.openai.completions.OpenAIChatCompletionModelOptions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Kimi 对 OpenAI Chat Completions 兼容参数的扩展。
 * <br>通用消息、工具和采样参数由父类处理，本类只负责 Kimi thinking、缓存和安全标识字段。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class KimiModelOptions extends OpenAIChatCompletionModelOptions {
    /** Kimi 官方 API base URL。 */
    public static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";

    /** 面向通用 Agent 场景的默认模型。 */
    public static final String DEFAULT_MODEL = "kimi-k2.6";

    /** Kimi 代码模型。 */
    public static final String KIMI_K2_7_CODE = "kimi-k2.7-code";

    /** metadata 中 thinking.keep 的字段名。 */
    public static final String THINKING_KEEP_METADATA = "kimi.thinkingKeep";

    /** metadata 中 prompt_cache_key 的字段名。 */
    public static final String PROMPT_CACHE_KEY_METADATA = "kimi.promptCacheKey";

    /** metadata 中 safety_identifier 的字段名。 */
    public static final String SAFETY_IDENTIFIER_METADATA = "kimi.safetyIdentifier";

    /** 是否在后续工具调用中保留模型思考内容。 */
    @Nullable
    private Boolean thinkingKeep;

    /** Prompt 缓存键。 */
    @Nullable
    private String promptCacheKey;

    /** 稳定且不可直接识别用户身份的安全标识。 */
    @Nullable
    private String safetyIdentifier;

    /**
     * 创建 Kimi 模型代码自带的 base options。
     *
     * @return Kimi base options
     */
    public static KimiModelOptions baseOptions() {
        var options = new KimiModelOptions();
        options.setProvider(KimiChatModel.PROVIDER);
        options.setModel(DEFAULT_MODEL);
        options.setBaseUrl(DEFAULT_BASE_URL);
        options.setApiPath(DEFAULT_API_PATH);
        options.setResponseFormat(LLMResponseFormat.TEXT);
        return options;
    }

    /**
     * 将任意模型参数复制为 Kimi 参数。
     *
     * @param options 源参数
     * @return Kimi 参数
     */
    public static KimiModelOptions copyOf(@Nullable ILLMModelOptions options) {
        var result = new KimiModelOptions();
        copyCommonOptions(result, options);
        copyChatCompletionOptions(result, options);
        copyKimiOptions(result, options);
        return result;
    }

    /**
     * 合并 Kimi base options 与调用覆盖参数。
     *
     * @param overrides 覆盖参数
     * @return 合并后的 Kimi 参数
     */
    @Override
    public KimiModelOptions mergeWith(@Nullable ILLMModelOptions overrides) {
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
        copyKimiOptions(result, overrides);
        return result;
    }

    /** @return thinking.keep Optional */
    public Optional<Boolean> findThinkingKeep() {
        return Optional.ofNullable(thinkingKeep);
    }

    /** @return Prompt 缓存键 Optional */
    public Optional<String> findPromptCacheKey() {
        return Optional.ofNullable(normalizeBlank(promptCacheKey));
    }

    /** @return 安全标识 Optional */
    public Optional<String> findSafetyIdentifier() {
        return Optional.ofNullable(normalizeBlank(safetyIdentifier));
    }

    /**
     * 获取 Kimi 参数，并将供应商字段转换为通用层可透传的 {@code body.} metadata。
     *
     * @return metadata 副本
     */
    @Override
    public Map<String, Object> getMetadata() {
        var metadata = new LinkedHashMap<>(super.getMetadata());
        metadata.remove(EXTRA_BODY_PREFIX + "thinking");
        metadata.remove(EXTRA_BODY_PREFIX + "prompt_cache_key");
        metadata.remove(EXTRA_BODY_PREFIX + "safety_identifier");

        putIfNotNull(metadata, THINKING_KEEP_METADATA, thinkingKeep);
        putIfNotBlank(metadata, PROMPT_CACHE_KEY_METADATA, promptCacheKey);
        putIfNotBlank(metadata, SAFETY_IDENTIFIER_METADATA, safetyIdentifier);

        var model = findModel().orElse(DEFAULT_MODEL);
        if (!model.startsWith(KIMI_K2_7_CODE)) {
            var thinking = new LinkedHashMap<String, Object>();
            findThinking().ifPresent(enabled -> thinking.put("type", enabled ? "enabled" : "disabled"));
            findThinkingKeep().ifPresent(keep -> {
                thinking.putIfAbsent("type", "enabled");
                thinking.put("keep", keep);
            });
            if (!thinking.isEmpty()) {
                metadata.put(EXTRA_BODY_PREFIX + "thinking", Map.copyOf(thinking));
            }
        }
        findPromptCacheKey().ifPresent(value -> metadata.put(EXTRA_BODY_PREFIX + "prompt_cache_key", value));
        findSafetyIdentifier().ifPresent(value -> metadata.put(EXTRA_BODY_PREFIX + "safety_identifier", value));
        return metadata;
    }

    /** @return metadata Optional */
    @Override
    public Optional<Map<String, Object>> findMetadata() {
        return Optional.of(getMetadata());
    }

    /**
     * 从专用字段或 metadata 复制 Kimi 参数。
     *
     * @param target 目标参数
     * @param source 源参数
     */
    private static void copyKimiOptions(KimiModelOptions target, @Nullable ILLMModelOptions source) {
        var metadata = source == null ? Map.<String, Object>of() : source.findMetadata().orElse(Map.of());
        if (source instanceof KimiModelOptions kimiOptions) {
            target.setThinkingKeep(firstPresentValue(
                    kimiOptions.getThinkingKeep(),
                    metadataBoolean(metadata, THINKING_KEEP_METADATA).orElse(null),
                    target.getThinkingKeep()));
            target.setPromptCacheKey(firstPresentText(
                    kimiOptions.getPromptCacheKey(),
                    metadataText(metadata, PROMPT_CACHE_KEY_METADATA),
                    target.getPromptCacheKey()));
            target.setSafetyIdentifier(firstPresentText(
                    kimiOptions.getSafetyIdentifier(),
                    metadataText(metadata, SAFETY_IDENTIFIER_METADATA),
                    target.getSafetyIdentifier()));
            return;
        }
        target.setThinkingKeep(metadataBoolean(metadata, THINKING_KEEP_METADATA).orElse(target.getThinkingKeep()));
        target.setPromptCacheKey(firstPresentText(
                metadataText(metadata, PROMPT_CACHE_KEY_METADATA), target.getPromptCacheKey(), null));
        target.setSafetyIdentifier(firstPresentText(
                metadataText(metadata, SAFETY_IDENTIFIER_METADATA), target.getSafetyIdentifier(), null));
    }
}
