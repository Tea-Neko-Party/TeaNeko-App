package org.zexnocs.teanekoagent.llm.framework.model;

import lombok.*;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 大语言模型请求参数的通用实现类。
 * <br>用于在不同模型供应商之间传递统一的模型参数和扩展数据。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LLMModelOptions implements ILLMModelOptions {
    /**
     * 供应商级模型适配器 ID。
     * <br>例如 {@code openai}、{@code deepseek}。
     */
    @Nullable
    private String provider;

    /**
     * 供应商侧具体模型名称。
     * <br>例如 {@code gpt-4.1}、{@code deepseek-chat}。
     */
    @Nullable
    private String model;

    /**
     * 是否启用思考或推理模式。
     * <br>该字段为 {@code null} 时表示调用方未显式设置。
     */
    @Nullable
    private Boolean thinking;

    /**
     * 模型最多生成的 token 数量。
     * <br>该字段为 {@code null} 时表示使用模型或供应商默认值。
     */
    @Nullable
    private Integer maxTokens;

    /**
     * 频率惩罚值。
     * <br>用于降低模型重复生成已经频繁出现内容的概率。
     */
    @Nullable
    private Double frequencyPenalty;

    /**
     * 采样温度。
     * <br>值越高输出通常越随机，值越低输出通常越稳定。
     */
    @Nullable
    private Double temperature;

    /**
     * nucleus sampling 的 top-p 参数。
     * <br>模型会从累计概率不超过该阈值的 token 集合中采样。
     */
    @Nullable
    private Double topP;

    /**
     * 存在惩罚值。
     * <br>用于影响模型生成新主题内容而不是重复已有内容的倾向。
     */
    @Nullable
    private Double presencePenalty;

    /**
     * 模型输出格式。
     * <br>默认使用纯文本格式。
     */
    @Builder.Default
    private LLMResponseFormat responseFormat = LLMResponseFormat.TEXT;

    /**
     * 停止词列表。
     * <br>模型生成任意停止词后应停止继续生成内容。
     */
    @Nullable
    private List<String> stopWords;

    /**
     * 是否启用流式响应。
     * <br>该字段为 {@code null} 时表示调用方未显式设置。
     */
    @Nullable
    private Boolean stream;

    /**
     * 允许模型调用的工具列表。
     * <br>主要用于 Function Tool 场景。
     */
    @Nullable
    private List<ILLMTool> tools;

    /**
     * 工具调用策略。
     * <br>常见值包括 {@code none}、{@code auto}、{@code required}。
     */
    @Nullable
    private String toolChoice;

    /**
     * 是否要求模型返回 token 的对数概率信息。
     * <br>该字段为 {@code null} 时表示调用方未显式设置。
     */
    @Nullable
    private Boolean logprobs;

    /**
     * 供应商扩展参数。
     * <br>用于承载通用字段无法覆盖的私有 API 参数。
     */
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    /**
     * 创建一个不包含任何显式参数的 options。
     *
     * @return 空的 {@link LLMModelOptions}
     */
    public static LLMModelOptions empty() {
        return builder().build();
    }

    /**
     * 将任意 {@link ILLMModelOptions} 复制为标准 {@link LLMModelOptions}。
     * <br>复制时只读取原 options 中实际提供的字段；未提供的字段保持为 {@code null} 或默认值。
     *
     * @param options 要复制的 options；为 {@code null} 时返回空 options
     * @return 复制后的标准 options
     */
    public static LLMModelOptions copyOf(@Nullable ILLMModelOptions options) {
        if (options == null) {
            return empty();
        }
        if (options instanceof LLMModelOptions standardOptions) {
            return standardOptions.toBuilder().build();
        }
        return builder()
                .provider(options.findProvider().orElse(null))
                .model(options.findModel().orElse(null))
                .thinking(options.findThinking().orElse(null))
                .maxTokens(options.findMaxTokens().orElse(null))
                .frequencyPenalty(options.findFrequencyPenalty().orElse(null))
                .temperature(options.findTemperature().orElse(null))
                .topP(options.findTopP().orElse(null))
                .presencePenalty(options.findPresencePenalty().orElse(null))
                .responseFormat(options.findResponseFormat().orElse(LLMResponseFormat.TEXT))
                .stopWords(options.findStopWords().orElse(null))
                .stream(options.findStream().orElse(null))
                .tools(options.findTools().orElse(null))
                .toolChoice(options.findToolChoice().orElse(null))
                .logprobs(options.findLogprobs().orElse(null))
                .metadata(options.findMetadata().orElse(Map.of()))
                .build();
    }

    /**
     * 合并默认 options 和覆盖 options。
     * <br>当 {@code overrides} 提供某字段时使用覆盖值，否则保留 {@code defaults} 中的值。
     *
     * @param defaults 默认 options；为 {@code null} 时视为空 options
     * @param overrides 覆盖 options；为 {@code null} 时直接返回默认 options 的副本
     * @return 合并后的标准 options
     */
    public static LLMModelOptions merge(@Nullable ILLMModelOptions defaults,
                                        @Nullable ILLMModelOptions overrides) {
        var base = copyOf(defaults);
        if (overrides == null) {
            return base;
        }
        var metadata = new java.util.LinkedHashMap<String, Object>();
        if (base.metadata != null) {
            metadata.putAll(base.metadata);
        }
        overrides.findMetadata().ifPresent(metadata::putAll);
        return base.toBuilder()
                .provider(overrides.findProvider().orElse(base.provider))
                .model(overrides.findModel().orElse(base.model))
                .thinking(overrides.findThinking().orElse(base.thinking))
                .maxTokens(overrides.findMaxTokens().orElse(base.maxTokens))
                .frequencyPenalty(overrides.findFrequencyPenalty().orElse(base.frequencyPenalty))
                .temperature(overrides.findTemperature().orElse(base.temperature))
                .topP(overrides.findTopP().orElse(base.topP))
                .presencePenalty(overrides.findPresencePenalty().orElse(base.presencePenalty))
                .responseFormat(overrides.findResponseFormat().orElse(base.responseFormat))
                .stopWords(overrides.findStopWords().orElse(base.stopWords))
                .stream(overrides.findStream().orElse(base.stream))
                .tools(overrides.findTools().orElse(base.tools))
                .toolChoice(overrides.findToolChoice().orElse(base.toolChoice))
                .logprobs(overrides.findLogprobs().orElse(base.logprobs))
                .metadata(new java.util.LinkedHashMap<>(metadata))
                .build();
    }

    /**
     * 获取供应商级模型适配器 ID。
     *
     * @return 供应商级模型适配器 ID
     * @throws UnsupportedOperationException 当前对象未设置 {@link #provider} 时抛出
     */
    @Override
    public String getProvider() {
        return require(provider, "provider");
    }

    /**
     * 获取模型名称。
     *
     * @return 模型名称
     * @throws UnsupportedOperationException 当前对象未设置 {@link #model} 时抛出
     */
    @Override
    public String getModel() {
        return require(model, "model");
    }

    /**
     * 获取是否启用思考或推理模式。
     *
     * @return 是否启用思考模式
     * @throws UnsupportedOperationException 当前对象未设置 {@link #thinking} 时抛出
     */
    @Override
    public boolean isThinking() {
        return require(thinking, "thinking");
    }

    /**
     * 获取模型最多生成的 token 数量。
     *
     * @return 最大输出 token 数量
     * @throws UnsupportedOperationException 当前对象未设置 {@link #maxTokens} 时抛出
     */
    @Override
    public int getMaxTokens() {
        return require(maxTokens, "maxTokens");
    }

    /**
     * 获取频率惩罚值。
     *
     * @return 频率惩罚值
     * @throws UnsupportedOperationException 当前对象未设置 {@link #frequencyPenalty} 时抛出
     */
    @Override
    public double getFrequencyPenalty() {
        return require(frequencyPenalty, "frequencyPenalty");
    }

    /**
     * 获取采样温度。
     *
     * @return 采样温度
     * @throws UnsupportedOperationException 当前对象未设置 {@link #temperature} 时抛出
     */
    @Override
    public double getTemperature() {
        return require(temperature, "temperature");
    }

    /**
     * 获取 top-p 参数。
     *
     * @return top-p 参数
     * @throws UnsupportedOperationException 当前对象未设置 {@link #topP} 时抛出
     */
    @Override
    public double getTopP() {
        return require(topP, "topP");
    }

    /**
     * 获取存在惩罚值。
     *
     * @return 存在惩罚值
     * @throws UnsupportedOperationException 当前对象未设置 {@link #presencePenalty} 时抛出
     */
    @Override
    public double getPresencePenalty() {
        return require(presencePenalty, "presencePenalty");
    }

    /**
     * 获取停止词列表。
     *
     * @return 停止词列表
     * @throws UnsupportedOperationException 当前对象未设置 {@link #stopWords} 时抛出
     */
    @Override
    public List<String> getStopWords() {
        return require(stopWords, "stopWords");
    }

    /**
     * 获取是否启用流式响应。
     *
     * @return 是否启用流式响应
     * @throws UnsupportedOperationException 当前对象未设置 {@link #stream} 时抛出
     */
    @Override
    public boolean isStream() {
        return require(stream, "stream");
    }

    /**
     * 获取允许模型调用的工具列表。
     *
     * @return 工具列表
     * @throws UnsupportedOperationException 当前对象未设置 {@link #tools} 时抛出
     */
    @Override
    public List<ILLMTool> getTools() {
        return require(tools, "tools");
    }

    /**
     * 获取工具调用策略。
     *
     * @return 工具调用策略
     * @throws UnsupportedOperationException 当前对象未设置 {@link #toolChoice} 时抛出
     */
    @Override
    public String getToolChoice() {
        return require(toolChoice, "toolChoice");
    }

    /**
     * 获取是否要求模型返回 token 的对数概率信息。
     *
     * @return 是否返回 logprobs
     * @throws UnsupportedOperationException 当前对象未设置 {@link #logprobs} 时抛出
     */
    @Override
    public boolean isLogprobs() {
        return require(logprobs, "logprobs");
    }

    /**
     * 获取供应商扩展参数。
     *
     * @return 供应商扩展参数
     */
    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 查找供应商级模型适配器 ID。
     *
     * @return {@link #provider} 的 Optional 包装
     */
    @Override
    public Optional<String> findProvider() {
        return Optional.ofNullable(provider);
    }

    /**
     * 查找模型名称。
     *
     * @return {@link #model} 的 Optional 包装
     */
    @Override
    public Optional<String> findModel() {
        return Optional.ofNullable(model);
    }

    /**
     * 查找思考模式开关。
     *
     * @return {@link #thinking} 的 Optional 包装
     */
    @Override
    public Optional<Boolean> findThinking() {
        return Optional.ofNullable(thinking);
    }

    /**
     * 查找最大输出 token 数量。
     *
     * @return {@link #maxTokens} 的 Optional 包装
     */
    @Override
    public Optional<Integer> findMaxTokens() {
        return Optional.ofNullable(maxTokens);
    }

    /**
     * 查找频率惩罚值。
     *
     * @return {@link #frequencyPenalty} 的 Optional 包装
     */
    @Override
    public Optional<Double> findFrequencyPenalty() {
        return Optional.ofNullable(frequencyPenalty);
    }

    /**
     * 查找采样温度。
     *
     * @return {@link #temperature} 的 Optional 包装
     */
    @Override
    public Optional<Double> findTemperature() {
        return Optional.ofNullable(temperature);
    }

    /**
     * 查找 top-p 参数。
     *
     * @return {@link #topP} 的 Optional 包装
     */
    @Override
    public Optional<Double> findTopP() {
        return Optional.ofNullable(topP);
    }

    /**
     * 查找存在惩罚值。
     *
     * @return {@link #presencePenalty} 的 Optional 包装
     */
    @Override
    public Optional<Double> findPresencePenalty() {
        return Optional.ofNullable(presencePenalty);
    }

    /**
     * 查找模型输出格式。
     *
     * @return {@link #responseFormat} 的 Optional 包装
     */
    @Override
    public Optional<LLMResponseFormat> findResponseFormat() {
        return Optional.ofNullable(responseFormat);
    }

    /**
     * 查找停止词列表。
     *
     * @return {@link #stopWords} 的 Optional 包装
     */
    @Override
    public Optional<List<String>> findStopWords() {
        return Optional.ofNullable(stopWords);
    }

    /**
     * 查找流式响应开关。
     *
     * @return {@link #stream} 的 Optional 包装
     */
    @Override
    public Optional<Boolean> findStream() {
        return Optional.ofNullable(stream);
    }

    /**
     * 查找允许模型调用的工具列表。
     *
     * @return {@link #tools} 的 Optional 包装
     */
    @Override
    public Optional<List<ILLMTool>> findTools() {
        return Optional.ofNullable(tools);
    }

    /**
     * 查找工具调用策略。
     *
     * @return {@link #toolChoice} 的 Optional 包装
     */
    @Override
    public Optional<String> findToolChoice() {
        return Optional.ofNullable(toolChoice);
    }

    /**
     * 查找 logprobs 开关。
     *
     * @return {@link #logprobs} 的 Optional 包装
     */
    @Override
    public Optional<Boolean> findLogprobs() {
        return Optional.ofNullable(logprobs);
    }

    /**
     * 查找供应商扩展参数。
     *
     * @return {@link #metadata} 的 Optional 包装
     */
    @Override
    public Optional<Map<String, Object>> findMetadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * 校验字段值是否存在。
     *
     * @param value 要校验的字段值
     * @param fieldName 字段名称，用于构造异常信息
     * @return 非空字段值
     * @param <T> 字段值类型
     * @throws UnsupportedOperationException 当字段值为 {@code null} 时抛出
     */
    private static <T> T require(@Nullable T value, String fieldName) {
        if (value == null) {
            throw new UnsupportedOperationException(fieldName + " is not set");
        }
        return value;
    }
}
