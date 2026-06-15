package org.zexnocs.teanekoagent.llm.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单个大语言模型适配器的文件配置项。
 * <br>用于把 YAML 中的默认调用参数转换为统一的 {@link LLMModelOptions}。
 * <br>新增一个供应商适配器时，通常只需要在 {@code models} 列表中添加一项：
 * <pre>{@code
 * models:
 *   - id: "deepseek"
 *     model: "deepseek-v4-flash"
 *     api-key: "${DEEPSEEK_API_KEY}"
 * }</pre>
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 */
@Getter
@Setter
@NoArgsConstructor
public class LLMModelFileConfigParameter {
    /**
     * 模型适配器注册 ID。
     * <br>一般与供应商 ID 相同，例如 {@code openai}、{@code deepseek}。
     * <br>该值必须与模型适配器注册到 {@code LLMModelService} 中的 ID 一致，不再使用 {@code provider/model} 格式。
     */
    @Nullable
    private String id;

    /**
     * 模型供应商标识。
     * <br>该字段仅作为 {@link #id} 未设置时的兼容写法；推荐直接使用 {@link #id}。
     */
    @Nullable
    private String provider;

    /**
     * 默认模型名称。
     * <br>该字段写入 {@link LLMModelOptions#getModel()}，用于覆盖模型适配器代码中的默认模型名，例如把 {@code deepseek-v4-flash} 改为其他兼容模型。
     * <br>该字段不参与模型适配器路由，路由只由 {@link #id} 决定。
     */
    @Nullable
    private String model;

    /**
     * 通用 API 配置。
     * <br>具体含义由模型供应商适配器解释，例如 API endpoint 或 API name。
     */
    @Nullable
    private String api;

    /**
     * API key。
     * <br>不会写入代码；模型适配器应从 options metadata 或数据库中读取该值。
     */
    @Nullable
    private String apiKey;

    /**
     * 供应商 API base URL。
     */
    @Nullable
    private String baseUrl;

    /**
     * 是否启用思考或推理模式。
     */
    @Nullable
    private Boolean thinking;

    /**
     * 模型最多生成的 token 数量。
     */
    @Nullable
    private Integer maxTokens;

    /**
     * 频率惩罚值。
     */
    @Nullable
    private Double frequencyPenalty;

    /**
     * 采样温度。
     */
    @Nullable
    private Double temperature;

    /**
     * nucleus sampling 的 top-p 参数。
     */
    @Nullable
    private Double topP;

    /**
     * 存在惩罚值。
     */
    @Nullable
    private Double presencePenalty;

    /**
     * 模型输出格式。
     */
    @Nullable
    private LLMResponseFormat responseFormat;

    /**
     * 停止词列表。
     */
    @Nullable
    private List<String> stopWords;

    /**
     * 是否启用流式响应。
     */
    @Nullable
    private Boolean stream;

    /**
     * 工具调用策略。
     */
    @Nullable
    private String toolChoice;

    /**
     * 是否要求模型返回 token 的对数概率信息。
     */
    @Nullable
    private Boolean logprobs;

    /**
     * 供应商扩展参数。
     * <br>不属于通用 options 的字段应放在这里，供应商适配器按约定读取。
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * 查找该配置项对应的模型适配器 ID。
     * <br>优先使用 {@link #id}；当 {@link #id} 未设置时，使用 {@link #provider} 作为兼容回退。
     *
     * @return 模型适配器 ID
     */
    public Optional<LLMModelId> findModelId() {
        var parsedId = LLMModelFileConfig.parseModelId(id);
        if (parsedId.isPresent()) {
            return parsedId;
        }
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(LLMModelId.of(provider));
    }

    /**
     * 将该配置项转换为统一模型调用参数。
     * <br>转换时会先保留代码默认 options，再用文件配置覆盖已声明字段。
     *
     * @param codeDefaults 代码中声明的模型默认 options
     * @return 合并后的模型调用参数
     */
    public LLMModelOptions toOptions(ILLMModelOptions codeDefaults) {
        var modelId = findModelId();
        var mergedMetadata = new LinkedHashMap<String, Object>();
        if (codeDefaults != null) {
            codeDefaults.findMetadata().ifPresent(mergedMetadata::putAll);
        }
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        putIfNotBlank(mergedMetadata, "api", api);
        putIfNotBlank(mergedMetadata, "apiKey", apiKey);
        putIfNotBlank(mergedMetadata, "baseUrl", baseUrl);
        var overrides = LLMModelOptions.builder()
                .provider(modelId.map(LLMModelId::id).orElse(blankToNull(provider)))
                .model(blankToNull(model))
                .thinking(thinking)
                .maxTokens(maxTokens)
                .frequencyPenalty(frequencyPenalty)
                .temperature(temperature)
                .topP(topP)
                .presencePenalty(presencePenalty)
                .responseFormat(responseFormat)
                .stopWords(stopWords)
                .stream(stream)
                .toolChoice(blankToNull(toolChoice))
                .logprobs(logprobs)
                .metadata(new LinkedHashMap<>(mergedMetadata))
                .build();
        return LLMModelOptions.merge(codeDefaults, overrides);
    }

    /**
     * 当字符串非空时写入 metadata。
     *
     * @param metadata metadata 映射
     * @param key metadata key
     * @param value metadata value
     */
    private static void putIfNotBlank(Map<String, Object> metadata, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    /**
     * 将空白字符串视为未配置。
     *
     * @param value 配置值
     * @return 非空白字符串；空白时返回 {@code null}
     */
    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
