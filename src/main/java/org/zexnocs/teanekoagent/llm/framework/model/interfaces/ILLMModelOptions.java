package org.zexnocs.teanekoagent.llm.framework.model.interfaces;

import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 大语言模型请求参数接口。
 * <br>提供通用参数读取方法，并通过 Optional 风格方法支持供应商按需合并参数。
 *
 * @author zExNocs
 * @date 2026/03/23
 * @since 4.4.0
 */
public interface ILLMModelOptions {
    /**
     * 获取模型所属的供应商标识。
     * <br>例如 {@code openai}、{@code deepseek}。
     *
     * @return 供应商标识
     * @throws UnsupportedOperationException 当前 options 未提供供应商标识时抛出
     */
    default String getProvider() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取模型名称。
     * <br>例如 {@code gpt-4.1}、{@code deepseek-chat}。
     *
     * @return 模型名称
     * @throws UnsupportedOperationException 当前 options 未提供模型名称时抛出
     */
    default String getModel() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取是否启用思考或推理模式。
     *
     * @return 是否启用思考模式
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default boolean isThinking() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取模型最多生成的 token 数量。
     *
     * @return 最大输出 token 数量
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default int getMaxTokens() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取频率惩罚值。
     * <br>该值用于降低模型重复生成已经频繁出现内容的概率。
     *
     * @return 频率惩罚值
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default double getFrequencyPenalty() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取采样温度。
     * <br>值越高，输出通常越随机；值越低，输出通常越稳定。
     *
     * @return 采样温度
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default double getTemperature() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取 nucleus sampling 的 top-p 参数。
     * <br>模型会从累计概率不超过该阈值的 token 集合中采样。
     *
     * @return top-p 参数
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default double getTopP() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取存在惩罚值。
     * <br>该值用于影响模型生成新主题内容而不是重复已有内容的倾向。
     *
     * @return 存在惩罚值
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default double getPresencePenalty() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取模型输出格式。
     *
     * @return 输出格式，默认值为 {@link LLMResponseFormat#TEXT}
     */
    default LLMResponseFormat getResponseFormat() {
        return LLMResponseFormat.TEXT;
    }

    /**
     * 获取停止词列表。
     * <br>模型生成任意停止词后应停止继续生成内容。
     *
     * @return 停止词列表
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default List<String> getStopWords() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取是否启用流式响应。
     *
     * @return 是否启用流式响应
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default boolean isStream() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取允许模型调用的工具列表。
     *
     * @return 工具列表
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default List<ILLMTool> getTools() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取工具调用策略。
     * <br>常见值包括 {@code none}、{@code auto}、{@code required}。
     *
     * @return 工具调用策略
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default String getToolChoice() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取是否要求模型返回 token 的对数概率信息。
     *
     * @return 是否返回 logprobs
     * @throws UnsupportedOperationException 当前 options 未提供该参数时抛出
     */
    default boolean isLogprobs() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取供应商扩展参数。
     * <br>用于承载通用 options 字段无法覆盖的 API key、base URL 或供应商私有参数。
     *
     * @return 供应商扩展参数
     */
    default Map<String, Object> getMetadata() {
        return Map.of();
    }

    /**
     * 尝试获取供应商标识。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 供应商标识 Optional
     */
    default Optional<String> findProvider() {
        try {
            return Optional.ofNullable(getProvider());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取模型名称。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 模型名称 Optional
     */
    default Optional<String> findModel() {
        try {
            return Optional.ofNullable(getModel());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取是否启用思考模式。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 思考模式开关 Optional
     */
    default Optional<Boolean> findThinking() {
        try {
            return Optional.of(isThinking());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取最大输出 token 数量。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 最大输出 token 数量 Optional
     */
    default Optional<Integer> findMaxTokens() {
        try {
            return Optional.of(getMaxTokens());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取频率惩罚值。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 频率惩罚值 Optional
     */
    default Optional<Double> findFrequencyPenalty() {
        try {
            return Optional.of(getFrequencyPenalty());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取采样温度。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 采样温度 Optional
     */
    default Optional<Double> findTemperature() {
        try {
            return Optional.of(getTemperature());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取 top-p 参数。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return top-p 参数 Optional
     */
    default Optional<Double> findTopP() {
        try {
            return Optional.of(getTopP());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取存在惩罚值。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 存在惩罚值 Optional
     */
    default Optional<Double> findPresencePenalty() {
        try {
            return Optional.of(getPresencePenalty());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 获取模型输出格式 Optional。
     * <br>默认实现始终返回 {@link LLMResponseFormat#TEXT}。
     *
     * @return 输出格式 Optional
     */
    default Optional<LLMResponseFormat> findResponseFormat() {
        return Optional.ofNullable(getResponseFormat());
    }

    /**
     * 尝试获取停止词列表。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 停止词列表 Optional
     */
    default Optional<List<String>> findStopWords() {
        try {
            return Optional.ofNullable(getStopWords());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取是否启用流式响应。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 流式响应开关 Optional
     */
    default Optional<Boolean> findStream() {
        try {
            return Optional.of(isStream());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取允许模型调用的工具列表。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 工具列表 Optional
     */
    default Optional<List<ILLMTool>> findTools() {
        try {
            return Optional.ofNullable(getTools());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取工具调用策略。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 工具调用策略 Optional
     */
    default Optional<String> findToolChoice() {
        try {
            return Optional.ofNullable(getToolChoice());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取是否要求返回 logprobs。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return logprobs 开关 Optional
     */
    default Optional<Boolean> findLogprobs() {
        try {
            return Optional.of(isLogprobs());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 尝试获取供应商扩展参数。
     * <br>默认实现返回 {@link #getMetadata()}。
     *
     * @return 供应商扩展参数 Optional
     */
    default Optional<Map<String, Object>> findMetadata() {
        return Optional.ofNullable(getMetadata());
    }
}
