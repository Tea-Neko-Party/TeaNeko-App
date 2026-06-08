package org.zexnocs.teanekoagent.llm.instance.deepseek;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 对话补全 API 响应数据。
 * <br>用于承载 DeepSeek {@code /chat/completions} 返回值，再由 mapper 转换为框架统一的 LLM response。
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 */
@Getter
@Setter
@NoArgsConstructor
public class DeepSeekChatCompletionResponseData implements IAPIResponseData {
    /**
     * 响应 ID。
     */
    private String id;

    /**
     * 响应对象类型。
     */
    private String object;

    /**
     * 响应创建时间戳，单位为秒。
     */
    private long created;

    /**
     * 响应使用的模型名称。
     */
    private String model;

    /**
     * 候选回复列表。
     */
    private List<Choice> choices;

    /**
     * token 用量信息。
     */
    private Usage usage;

    /**
     * 单个候选回复。
     *
     * @author zExNocs
     * @date 2026/06/08
     * @since 4.4.0
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Choice {
        /**
         * 候选项索引。
         */
        private int index;

        /**
         * 助手消息。
         */
        private Message message;

        /**
         * 结束原因。
         */
        @JsonProperty("finish_reason")
        private String finishReason;

        /**
         * token 对数概率信息。
         */
        private Map<String, Object> logprobs;
    }

    /**
     * DeepSeek assistant 消息。
     *
     * @author zExNocs
     * @date 2026/06/08
     * @since 4.4.0
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Message {
        /**
         * 消息角色。
         */
        private String role;

        /**
         * 文本内容。
         */
        private String content;

        /**
         * 推理模型返回的推理内容。
         */
        @JsonProperty("reasoning_content")
        private String reasoningContent;

        /**
         * 模型发起的工具调用列表。
         */
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }

    /**
     * DeepSeek 工具调用。
     *
     * @author zExNocs
     * @date 2026/06/08
     * @since 4.4.0
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ToolCall {
        /**
         * 工具调用 ID。
         */
        private String id;

        /**
         * 工具调用类型，通常为 {@code function}。
         */
        private String type;

        /**
         * function 调用信息。
         */
        private FunctionCall function;
    }

    /**
     * DeepSeek function 调用信息。
     *
     * @author zExNocs
     * @date 2026/06/08
     * @since 4.4.0
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FunctionCall {
        /**
         * function 名称。
         */
        private String name;

        /**
         * function 参数 JSON 字符串。
         */
        private String arguments;
    }

    /**
     * DeepSeek token 用量信息。
     *
     * @author zExNocs
     * @date 2026/06/08
     * @since 4.4.0
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Usage {
        /**
         * completion token 数量。
         */
        @JsonProperty("completion_tokens")
        private int completionTokens;

        /**
         * prompt token 数量。
         */
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        /**
         * 总 token 数量。
         */
        @JsonProperty("total_tokens")
        private int totalTokens;

        /**
         * prompt 缓存命中 token 数量。
         */
        @JsonProperty("prompt_cache_hit_tokens")
        private int promptCacheHitTokens;

        /**
         * prompt 缓存未命中 token 数量。
         */
        @JsonProperty("prompt_cache_miss_tokens")
        private int promptCacheMissTokens;

        /**
         * completion 详情。
         */
        @JsonProperty("completion_tokens_details")
        private TokenDetails completionTokensDetails;
    }

    /**
     * token 详情。
     *
     * @author zExNocs
     * @date 2026/06/08
     * @since 4.4.0
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class TokenDetails {
        /**
         * 推理 token 数量。
         */
        @JsonProperty("reasoning_tokens")
        private int reasoningTokens;
    }
}
