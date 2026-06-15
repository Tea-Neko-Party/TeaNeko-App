package org.zexnocs.teanekoagent.llm.instance.openai.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 响应数据。
 * <br>只声明统一 LLM framework 需要消费的响应字段，未知字段由 Jackson 忽略。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class OpenAIResponsesResponseData implements IAPIResponseData {
    /** 响应 ID。 */
    private String id;

    /** 响应对象类型。 */
    private String object;

    /** 响应创建时间戳，单位为秒。 */
    @JsonProperty("created_at")
    private long createdAt;

    /** 响应使用的模型名称。 */
    private String model;

    /** 响应状态。 */
    private String status;

    /** 响应输出 item 列表。 */
    private List<OutputItem> output;

    /** token 用量。 */
    private Usage usage;

    /** 未完整生成的原因。 */
    @JsonProperty("incomplete_details")
    private IncompleteDetails incompleteDetails;

    /**
     * Responses API 输出 item。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OutputItem {
        /** Item ID。 */
        private String id;

        /** Item 类型，例如 message、function_call。 */
        private String type;

        /** Item 状态。 */
        private String status;

        /** Message 角色。 */
        private String role;

        /** Message 内容列表。 */
        private List<OutputContent> content;

        /** Function call ID。 */
        @JsonProperty("call_id")
        private String callId;

        /** Function 名称。 */
        private String name;

        /** Function 参数 JSON 字符串。 */
        private String arguments;

        /** Reasoning summary 内容。 */
        private List<Map<String, Object>> summary;

        /** 无状态请求中需要回传的加密 reasoning 内容。 */
        @JsonProperty("encrypted_content")
        private String encryptedContent;
    }

    /**
     * Responses API message 输出内容。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OutputContent {
        /** 内容类型，例如 output_text、refusal。 */
        private String type;

        /** 输出文本。 */
        private String text;

        /** 拒绝说明。 */
        private String refusal;

        /** 输出文本 logprobs。 */
        private List<Map<String, Object>> logprobs;
    }

    /**
     * Responses API token 用量。
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
        @JsonProperty("input_tokens")
        private int inputTokens;

        /** 输入 token 详情。 */
        @JsonProperty("input_tokens_details")
        private InputTokenDetails inputTokensDetails;

        /** 输出 token 数量。 */
        @JsonProperty("output_tokens")
        private int outputTokens;

        /** 输出 token 详情。 */
        @JsonProperty("output_tokens_details")
        private OutputTokenDetails outputTokensDetails;

        /** 总 token 数量。 */
        @JsonProperty("total_tokens")
        private int totalTokens;
    }

    /**
     * OpenAI 输入 token 详情。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class InputTokenDetails {
        /** 缓存命中的输入 token 数量。 */
        @JsonProperty("cached_tokens")
        private int cachedTokens;
    }

    /**
     * OpenAI 输出 token 详情。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OutputTokenDetails {
        /** 推理 token 数量。 */
        @JsonProperty("reasoning_tokens")
        private int reasoningTokens;
    }

    /**
     * OpenAI 未完整生成详情。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class IncompleteDetails {
        /** 未完成原因。 */
        private String reason;
    }
}
