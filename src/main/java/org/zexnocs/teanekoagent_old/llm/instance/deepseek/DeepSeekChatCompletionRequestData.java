package org.zexnocs.teanekoagent_old.llm.instance.deepseek;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 对话补全 API 请求数据。
 * <br>该类通过 {@link org.zexnocs.teanekocore.api_response.APIResponseService} 发送请求。
 * <br>base URL、API path 与 API key 来自运行时配置，不参与 JSON 请求体序列化。
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@APIRequestData(
        baseUrl = "",
        method = "POST",
        timeoutInMillis = 60000L,
        retryCount = 0
)
public class DeepSeekChatCompletionRequestData implements IAPIRequestData {
    /**
     * DeepSeek API base URL。
     * <br>来自 {@link DeepSeekModelOptions#DEFAULT_BASE_URL}。
     */
    @JsonIgnore
    private String baseUrl;

    /**
     * DeepSeek API path。
     * <br>来自 LLM file config 的 metadata {@code api}，默认值通常为 {@code /chat/completions}。
     */
    @JsonIgnore
    private String apiPath;

    /**
     * DeepSeek API key。
     * <br>来自 LLM file config 的 metadata {@code apiKey}。
     */
    @JsonIgnore
    private String apiKey;

    /**
     * 本次调用使用的 DeepSeek 模型名称。
     */
    private String model;

    /**
     * 本次对话消息列表。
     */
    private List<Map<String, Object>> messages;

    /**
     * 是否启用推理或思考模式。
     * <br>DeepSeek 文档中该字段为对象结构，例如 {@code {"type":"enabled"}}。
     */
    private Map<String, Object> thinking;

    /**
     * 最大输出 token 数量。
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 频率惩罚值。
     */
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    /**
     * 采样温度。
     */
    private Double temperature;

    /**
     * nucleus sampling 的 top-p 参数。
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 存在惩罚值。
     */
    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    /**
     * 停止词。
     */
    private List<String> stop;

    /**
     * 是否启用流式响应。
     */
    private Boolean stream;

    /**
     * 输出格式。
     */
    @JsonProperty("response_format")
    private Map<String, String> responseFormat;

    /**
     * Function Tool 定义列表。
     */
    private List<Map<String, Object>> tools;

    /**
     * 工具调用策略。
     */
    @JsonProperty("tool_choice")
    private String toolChoice;

    /**
     * 是否返回 token 对数概率。
     */
    private Boolean logprobs;

    /**
     * 返回的候选 token 对数概率数量。
     */
    @JsonProperty("top_logprobs")
    private Integer topLogprobs;

    /**
     * 流式响应配置。
     * <br>例如 {@code {"include_usage":true}}。
     */
    @JsonProperty("stream_options")
    private Map<String, Object> streamOptions;

    /**
     * DeepSeek 请求体中的用户标识。
     */
    @JsonProperty("user_id")
    private String userId;

    /**
     * 额外请求体字段。
     * <br>来自 metadata 中以 {@code body.} 为前缀的字段，例如 {@code body.top_logprobs}。
     */
    @JsonIgnore
    @Builder.Default
    private Map<String, Object> extraBody = Map.of();

    /**
     * 获取额外请求体字段。
     *
     * @return 额外请求体字段
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody == null ? Map.of() : extraBody;
    }

    /**
     * 获取运行时 API base URL。
     *
     * @return DeepSeek API base URL
     */
    @Override
    @JsonIgnore
    public String getBaseUrlOverride() {
        return baseUrl;
    }

    /**
     * 获取运行时 API path。
     *
     * @return DeepSeek API path
     */
    @Override
    @JsonIgnore
    public String getPathOverride() {
        return apiPath;
    }

    /**
     * 获取请求头。
     *
     * @return Authorization 请求头
     */
    @Override
    @JsonIgnore
    public Map<String, String> headers() {
        return Map.of("Authorization", "Bearer " + apiKey);
    }
}
