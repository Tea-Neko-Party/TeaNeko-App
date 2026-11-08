package org.zexnocs.teanekoagent.llm.instance.openai.completions;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 兼容请求数据。
 * <br>endpoint、认证信息和额外请求头只用于 HTTP 调用，不会进入 JSON 请求体。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：响应格式字段支持完整嵌套 JSON Schema。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 * @version 4.9.0-l
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
        timeoutInMillis = 120000L,
        retryCount = 0
)
public class OpenAIChatCompletionRequestData implements IAPIRequestData {
    /** API base URL。 */
    @JsonIgnore
    private String baseUrl;

    /** Chat Completions API path。 */
    @JsonIgnore
    private String apiPath;

    /** API key。 */
    @JsonIgnore
    private String apiKey;

    /** OpenAI organization 请求头。 */
    @JsonIgnore
    private String organization;

    /** OpenAI project 请求头。 */
    @JsonIgnore
    private String project;

    /** 供应商额外请求头。 */
    @JsonIgnore
    @Builder.Default
    private Map<String, String> extraHeaders = Map.of();

    /** 本次调用使用的模型名称。 */
    private String model;

    /** Chat Completions 消息列表。 */
    private List<Map<String, Object>> messages;

    /** 最大输出 token 数量。 */
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;

    /** 采样温度。 */
    private Double temperature;

    /** nucleus sampling 的 top-p 参数。 */
    @JsonProperty("top_p")
    private Double topP;

    /** 频率惩罚值。 */
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    /** 存在惩罚值。 */
    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    /** 停止词列表。 */
    private List<String> stop;

    /** 是否启用流式响应。 */
    private Boolean stream;

    /** 输出格式。 */
    @JsonProperty("response_format")
    // 2026/11/06：Schema 模式包含嵌套结构，不能限制为字符串值。
    private Map<String, Object> responseFormat;

    /** Function Tool 定义。 */
    private List<Map<String, Object>> tools;

    /** 工具调用策略。 */
    @JsonProperty("tool_choice")
    private String toolChoice;

    /** 是否允许并行工具调用。 */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    /** 是否返回 token 对数概率。 */
    private Boolean logprobs;

    /** 返回每个 token 的候选 logprobs 数量。 */
    @JsonProperty("top_logprobs")
    private Integer topLogprobs;

    /** metadata 中通过 {@code body.} 前缀提供的额外请求字段。 */
    @JsonIgnore
    @Builder.Default
    private Map<String, Object> extraBody = Map.of();

    /**
     * 获取额外请求字段。
     *
     * @return 额外请求字段
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody == null ? Map.of() : extraBody;
    }

    /** @return API base URL */
    @Override
    @JsonIgnore
    public String getBaseUrlOverride() {
        return baseUrl;
    }

    /** @return Chat Completions API path */
    @Override
    @JsonIgnore
    public String getPathOverride() {
        return apiPath;
    }

    /**
     * 构造 Bearer 认证和供应商扩展请求头。
     *
     * @return HTTP 请求头
     */
    @Override
    @JsonIgnore
    public Map<String, String> headers() {
        var headers = new LinkedHashMap<String, String>();
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        headers.put("Authorization", "Bearer " + apiKey);
        putIfNotBlank(headers, "OpenAI-Organization", organization);
        putIfNotBlank(headers, "OpenAI-Project", project);
        return Map.copyOf(headers);
    }

    /**
     * 写入非空白请求头。
     *
     * @param target 请求头 map
     * @param key 请求头名称
     * @param value 请求头值
     */
    private static void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
