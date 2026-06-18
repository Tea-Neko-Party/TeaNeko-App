package org.zexnocs.teanekoagent.llm.instance.openai.responses;

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
 * OpenAI Responses API 请求数据。
 * <br>运行时 endpoint、认证信息和归属请求头不会进入 JSON 请求体。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
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
public class OpenAIResponsesRequestData implements IAPIRequestData {
    /** OpenAI API base URL。 */
    @JsonIgnore
    private String baseUrl;

    /** OpenAI Responses API path。 */
    @JsonIgnore
    private String apiPath;

    /** OpenAI API key。 */
    @JsonIgnore
    private String apiKey;

    /** OpenAI organization 请求头。 */
    @JsonIgnore
    private String organization;

    /** OpenAI project 请求头。 */
    @JsonIgnore
    private String project;

    /** 本次调用使用的模型名称。 */
    private String model;

    /** Responses API input item 列表。 */
    private List<Map<String, Object>> input;

    /** 最大输出 token 数量。 */
    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    /** Reasoning 模型配置。 */
    private Map<String, Object> reasoning;

    /** 采样温度。 */
    private Double temperature;

    /** nucleus sampling 的 top-p 参数。 */
    @JsonProperty("top_p")
    private Double topP;

    /** 文本输出格式和 verbosity。 */
    private Map<String, Object> text;

    /** Function Tool 定义列表。 */
    private List<Map<String, Object>> tools;

    /** 工具调用策略。 */
    @JsonProperty("tool_choice")
    private String toolChoice;

    /** 是否允许模型并行调用工具。 */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    /** 需要额外返回的响应字段。 */
    private List<String> include;

    /** 返回每个 token 的候选 logprobs 数量。 */
    @JsonProperty("top_logprobs")
    private Integer topLogprobs;

    /** 是否允许 OpenAI 存储响应。 */
    private Boolean store;

    /** Prompt 缓存键。 */
    @JsonProperty("prompt_cache_key")
    private String promptCacheKey;

    /** 安全检测稳定用户标识。 */
    @JsonProperty("safety_identifier")
    private String safetyIdentifier;

    /** 服务层级。 */
    @JsonProperty("service_tier")
    private String serviceTier;

    /** 上下文截断策略。 */
    private String truncation;

    /** OpenAI 请求 metadata。 */
    private Map<String, String> metadata;

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

    /** @return OpenAI API base URL */
    @Override
    @JsonIgnore
    public String getBaseUrlOverride() {
        return baseUrl;
    }

    /** @return Responses API path */
    @Override
    @JsonIgnore
    public String getPathOverride() {
        return apiPath;
    }

    /**
     * 构造 OpenAI API 请求头。
     *
     * @return Authorization 以及可选的 organization、project 请求头
     */
    @Override
    @JsonIgnore
    public Map<String, String> headers() {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer " + apiKey);
        putIfNotBlank(headers, "OpenAI-Organization", organization);
        putIfNotBlank(headers, "OpenAI-Project", project);
        return Map.copyOf(headers);
    }

    /**
     * 向请求头 map 写入非空白字符串。
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
