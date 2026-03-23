package org.zexnocs.teanekoagent.llm_api_framework.model.interfaces;

import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMTool;

import java.util.List;

/**
 * LLM 模型额外输入参数选项。
 * <br>用于给各自模型提供额外的输入参数选项。
 *
 * @author zExNocs
 * @date 2026/03/23
 * @since 4.4.0
 */
public interface ILLMModelOptions {
    /**
     * 获取模型类型。
     *
     * @return {@link String }
     * @throws UnsupportedOperationException 如果没有实现
     */
    default String getModel() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 是否开启思考模式
     *
     * @return boolean
     * @throws UnsupportedOperationException 如果没有实现
     */
    default boolean isThinking() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 生成 completion 的最大 token 数
     *
     * @return int
     * @throws UnsupportedOperationException 如果没有实现
     */
    default int getMaxTokens() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 模型重复相同内容的可能性
     * <br>如果该值为正，那么新 token 会根据其在已有文本中的出现频率受到相应的惩罚
     * <br>范围一般是 [-2.0,2.0]，默认为 0.0
     *
     * @return double
     * @throws UnsupportedOperationException 如果没有实现
     */
    default double getFrequencyPenalty() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 采样温度
     * <br>越高的值会使输出更随机，越低的值会使输出更集中和确定性
     * <br>通常建议更改这个值或者更改 top_p，但不建议同时对两者进行修改
     * <br>一般范围是[0.0, 2.0]，默认为 1.0
     *
     * @return double
     * @throws UnsupportedOperationException 如果没有实现
     */
    default double getTemperature() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * top_p 采样，调节温度的替代方案。
     * <br>模型会考虑前 `top_p` 累加概率的 token 结果。
     * <br>越小生成越保守、可预测性高；越大生成越多样化。
     * <br>例如 0.1 意味着只有包括在最高 10% 概率中的 token 才会被考虑
     * <br>通常建议更改这个值或者更改 temperature，但不建议同时对两者进行修改
     * <br>一般范围是 [0.0, 1.0]，默认为 1.0
     *
     * @return double
     * @throws UnsupportedOperationException 如果没有实现
     */
    default double getTopP() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 模型谈论新主题的可能性
     * <br>如果该值为正，那么新 token 会根据其是否已在已有文本中出现受到相应的惩罚
     * <br>一般范围是 [-2.0, 2.0]，默认为 0.0
     *
     * @return double
     * @throws UnsupportedOperationException 如果没有实现
     */
    default double getPresencePenalty() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 模型的输出格式
     * <br>默认为 {@link LLMResponseFormat#TEXT}
     *
     * @return {@link LLMResponseFormat }
     */
    default LLMResponseFormat getResponseFormat() {
        return LLMResponseFormat.TEXT;
    }

    /**
     * 停止词。
     * <br>在遇到这些词时模型将会停止生成更多的 token。
     *
     * @return {@link List }<{@link String }>
     * @throws UnsupportedOperationException 如果没有实现
     */
    default List<String> getStopWords() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 是否是流式响应
     * <br>如果设置为 True，会以 SSE（server-sent events）的形式以流式发送消息增量。
     *
     * @return boolean
     * @throws UnsupportedOperationException 如果没有实现
     */
    default boolean isStream() throws UnsupportedOperationException {
       throw new UnsupportedOperationException();
    }

    /**
     * 模型可能会调用的工具列表
     *
     * @return {@link List }<{@link ILLMTool }>
     */
    default List<ILLMTool> getTools() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 工具可选项，一般来说：
     * <br>1. "none": 不调用任何 tool，生成一条常规消息
     * <br>2. "auto": 模型可以选择生成一条常规消息，或者调用工具
     * <br>3. "required": 强制模型调用工具；可以设置必须调用的特定工具名
     *
     * @return {@link String }
     * @throws UnsupportedOperationException 如果没有实现
     */
    default String getToolChoice() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 是否输出对数概率
     * <br>如果设置为 true，响应中 `message` 的 `content` m返回每个输出的 token 及其对应的对数概率
     *
     * @return boolean
     * @throws UnsupportedOperationException 如果没有实现
     */
    default boolean isLogprobs() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

}
