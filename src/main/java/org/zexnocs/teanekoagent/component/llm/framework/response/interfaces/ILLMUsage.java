package org.zexnocs.teanekoagent.component.llm.framework.response.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * LLM 请求的用量信息。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 * @version 4.8.0-l
 */
public interface ILLMUsage {
    /**
     * 产生的 completion 所使用的 token 数量
     *
     * @return int
     */
    int getCompletionTokens();

    /**
     * 用户 prompt 包含的 token 数量
     * <br>该值等于 `prompt_cache_hit_tokens` + `prompt_cache_miss_tokens`
     *
     * @return int
     */
    int getPromptTokens();

    /**
     * 该请求中使用的总 token 数量
     * <br>等于 `prompt_tokens` + `completion_tokens`
     *
     * @return int
     */
    int getTotalTokens();

    /**
     * 用户 prompt 中命中缓存的 token 数量
     *
     * @return int
     */
    int getPromptCacheHitTokens();

    /**
     * 用户 prompt 中未命中缓存的 token 数量
     *
     * @return int
     */
    int getPromptCacheMissTokens();

    /**
     * 推理模型所产生的思维链 token 数量
     *
     * @return int
     */
    int getReasoningTokens();
}
