package org.zexnocs.teanekoagent.llm.framework.response;

import lombok.*;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMUsage;

/**
 * 大语言模型 token 用量信息。
 * <br>用于统一表示 prompt、completion、缓存命中和推理 token 等统计数据。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMUsage implements ILLMUsage {
    /**
     * 模型生成 completion 所消耗的 token 数量。
     */
    private int completionTokens;

    /**
     * 输入 prompt 所包含的 token 数量。
     * <br>通常等于缓存命中 token 与缓存未命中 token 的总和。
     */
    private int promptTokens;

    /**
     * 本次请求使用的总 token 数量。
     * <br>通常等于 prompt token 与 completion token 的总和。
     */
    private int totalTokens;

    /**
     * 输入 prompt 中命中缓存的 token 数量。
     */
    private int promptCacheHitTokens;

    /**
     * 输入 prompt 中未命中缓存的 token 数量。
     */
    private int promptCacheMissTokens;

    /**
     * 推理模型产生的推理 token 数量。
     */
    private int reasoningTokens;

    /**
     * 创建一个空的 token 用量对象。
     * <br>所有用量字段均为默认值 {@code 0}。
     *
     * @return 空 token 用量对象
     */
    public static LLMUsage empty() {
        return new LLMUsage();
    }
}
