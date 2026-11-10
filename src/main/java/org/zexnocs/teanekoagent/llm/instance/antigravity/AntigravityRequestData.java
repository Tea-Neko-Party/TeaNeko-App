package org.zexnocs.teanekoagent.llm.instance.antigravity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.NonNull;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;

import java.util.List;
import java.util.Map;

/**
 * Gemini 原生代理请求；可信连接及凭据仅进入 HTTP 边界，不进入 JSON 或调试文本。
 * <br>2026/11/10：注册无重试的原生请求，流式与非流式沿用同一 HTTP 生命周期。
 *
 * @param baseUrl 可信服务地址
 * @param apiPath 已固定模型的生成路径
 * @param apiKey 仅用于请求头的代理密钥
 * @param contents 对话内容
 * @param systemInstruction 可选系统指令
 * @param generationConfig 生成与结构约束
 * @param tools 工具声明
 * @param toolConfig 工具选择策略
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@APIRequestData(baseUrl = "", method = "POST", timeoutInMillis = 120000L, retryCount = 0)
public record AntigravityRequestData(@JsonIgnore String baseUrl, @JsonIgnore String apiPath,
        @JsonIgnore String apiKey, List<Map<String, Object>> contents, Map<String, Object> systemInstruction,
        Map<String, Object> generationConfig, List<Map<String, Object>> tools, Map<String, Object> toolConfig)
        implements IAPIRequestData {
    /** @return 可信配置解析的基础地址。 */
    @Override @JsonIgnore
    public String getBaseUrlOverride() {
        return baseUrl;
    }

    /** @return 已校验且包含当前模型的生成路径。 */
    @Override @JsonIgnore
    public String getPathOverride() {
        return apiPath;
    }

    /** @return 原生代理认证头，不把密钥放入 URL 查询串。 */
    @Override @JsonIgnore
    public Map<String, String> headers() {
        return Map.of("x-goog-api-key", apiKey);
    }

    /** @return 隐藏请求正文和认证信息的固定描述。 */
    @Override
    @NonNull
    public String toString() {
        return "AntigravityRequestData[内容及凭据已隐藏]";
    }
}
