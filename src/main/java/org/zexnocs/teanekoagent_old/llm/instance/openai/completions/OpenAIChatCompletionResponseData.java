package org.zexnocs.teanekoagent_old.llm.instance.openai.completions;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 兼容响应数据。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class OpenAIChatCompletionResponseData implements IAPIResponseData {
    /** 响应 ID。 */
    private String id;

    /** 响应对象类型。 */
    private String object;

    /** 响应创建时间戳，单位为秒。 */
    private long created;

    /** 实际响应模型名称。 */
    private String model;

    /** 候选回复列表。 */
    private List<Choice> choices;

    /** token 用量。 */
    private Usage usage;

    /**
     * 单个候选回复。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Choice {
        /** 候选项索引。 */
        private int index;

        /** assistant 消息。 */
        private Message message;

        /** 结束原因。 */
        @JsonProperty("finish_reason")
        private String finishReason;

        /** token 对数概率数据。 */
        private Map<String, Object> logprobs;
    }

    /**
     * Chat Completions assistant 消息。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Message {
        /** 消息角色。 */
        private String role;

        /** 参与者名称。 */
        private String name = "";

        /** 最终回答文本。 */
        private String content;

        /** 兼容供应商返回的推理内容。 */
        @JsonProperty("reasoning_content")
        private String reasoningContent;

        /** 模型发起的工具调用列表。 */
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }

    /**
     * Function Tool 调用。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ToolCall {
        /** 工具调用 ID。 */
        private String id;

        /** 工具调用类型。 */
        private String type;

        /** Function 调用信息。 */
        private FunctionCall function;
    }

    /**
     * Function 调用信息。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FunctionCall {
        /** Function 名称。 */
        private String name;

        /** Function 参数 JSON 字符串。 */
        private String arguments;
    }

    /**
     * Chat Completions token 用量。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Usage {
        /** 输入 token 数量。 */
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        /** 输出 token 数量。 */
        @JsonProperty("completion_tokens")
        private int completionTokens;

        /** 总 token 数量。 */
        @JsonProperty("total_tokens")
        private int totalTokens;

        /** Prompt token 详情。 */
        @JsonProperty("prompt_tokens_details")
        private PromptTokenDetails promptTokenDetails;

        /** Completion token 详情。 */
        @JsonProperty("completion_tokens_details")
        private CompletionTokenDetails completionTokenDetails;
    }

    /**
     * Prompt token 详情。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PromptTokenDetails {
        /** 缓存命中的 token 数量。 */
        @JsonProperty("cached_tokens")
        private int cachedTokens;
    }

    /**
     * Completion token 详情。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CompletionTokenDetails {
        /** 推理 token 数量。 */
        @JsonProperty("reasoning_tokens")
        private int reasoningTokens;
    }
}
