package org.zexnocs.teanekoagent.llm_api_framework.model.interfaces;

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
     * <br>默认不启动：并不是所有的模型都支持思考模式
     *
     * @return boolean
     */
    default boolean getThinking() {
        return false;
    }

    /**
     * 模型重复相同内容的可能性
     * <br>如果该值为正，那么新 token 会根据其在已有文本中的出现频率受到相应的惩罚
     * <br>范围一般是 [-2.0,2.0]，默认为 0.0
     *
     * @return {@link Double }
     * @throws UnsupportedOperationException 如果没有实现
     */
    default Double getFrequencyPenalty() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 生成 completion 的最大 token 数
     *
     * @return {@link Integer }
     * @throws UnsupportedOperationException 如果没有实现
     */
    default Integer getMaxTokens() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 模型谈论新主题的可能性
     * <br>如果该值为正，那么新 token 会根据其是否已在已有文本中出现受到相应的惩罚
     * <br>一般范围是 [-2.0, 2.0]，默认为 0.0
     *
     * @return {@link Double }
     * @throws UnsupportedOperationException 如果没有实现
     */
    default Double getPresencePenalty() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }


}
