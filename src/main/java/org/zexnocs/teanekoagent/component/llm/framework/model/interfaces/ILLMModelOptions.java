package org.zexnocs.teanekoagent.component.llm.framework.model.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.model.LLMContextWindow;
import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonSchema;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMTool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 大语言模型请求参数接口。
 * <br>提供通用参数读取方法，并通过 Optional 风格方法支持供应商按需合并参数。
 * <br>2026/11/04：传递模型上下文准入策略，保持供应商请求字段独立。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：统一携带 JSON 输出结构，不借助供应商私有 metadata。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 *
 * @author zExNocs
 * @date 2026/03/23
 * @since 4.4.0
 * @version 4.8.3-l
 */
public interface ILLMModelOptions {
    /** @return 初始化预编译的不可变输出结构，未要求结构时为空 */
    default Optional<LLMJsonSchema> findResponseSchema() {
        return Optional.empty();
    }

    /**
     * 2026/11/04：读取随有效模型配置固定的本地上下文策略。
     * @return 显式上下文声明，未提供时为空
     */
    default Optional<LLMContextWindow> findContextWindow() {
        return Optional.empty();
    }

    /**
     * 获取模型所属的供应商级适配器 ID。
     * <br>例如 {@code openai}、{@code deepseek}。
     *
     * @return 供应商级适配器 ID
     * @throws UnsupportedOperationException 当前 options 未提供供应商标识时抛出
     */
    default String getProvider() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取供应商侧具体模型名称。
     * <br>例如 {@code gpt-4.1}、{@code deepseek-v4-flash}。
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
     * 尝试获取供应商级适配器 ID。
     * <br>如果当前 options 未提供该参数，则返回空 Optional。
     *
     * @return 供应商级适配器 ID Optional
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
