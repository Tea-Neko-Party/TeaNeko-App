package org.zexnocs.teanekoagent.llm.framework.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * 一次请求使用的可信连接配置。只由凭据端口返回，不是可导出的模型选项。
 * 日志和 JSON 只显示脱敏标记；认证值只交给 HTTP 请求对象。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMProviderAccess {
    @JsonIgnore
    private final String apiKey;
    @JsonIgnore
    private final @Nullable String baseUrl;
    @JsonIgnore
    private final @Nullable String apiPath;

    /**
     * 校验可信 Spring 凭据及连接参数；拒绝非法认证值和可改变请求目标边界的地址。
     *
     * @param apiKey 仅供认证边界使用的本地凭据，禁止进入日志或普通消息
     * @param baseUrl 可信配置指定的服务地址
     * @param apiPath 可信配置指定的站内接口路径
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    public LLMProviderAccess(String apiKey, @Nullable String baseUrl, @Nullable String apiPath) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("${")
                || apiKey.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new LLMConfigurationException("LLM API credential is missing or invalid in Spring configuration.");
        }
        this.apiKey = apiKey;
        this.baseUrl = validateBaseUrl(baseUrl);
        this.apiPath = validateApiPath(apiPath);
    }

    /**
     * 仅向实际 HTTP 认证边界提供凭据；调用方不得将值写入 Prompt、普通选项或日志。
     *
     * @return 仅可交给请求认证边界的原始凭据
     */
    @JsonIgnore
    public String apiKey() {
        return apiKey;
    }

    /**
     * 优先使用已校验的本地连接地址，未配置时采用供应商内建地址。
     *
     * @param fallback 未显式配置时使用的可信默认值
     * @return 按本方法契约生成的文本或标识
     */
    public String baseUrlOr(String fallback) {
        return baseUrl == null ? fallback : baseUrl;
    }

    /**
     * 优先使用已校验的本地接口路径，未配置时采用供应商内建路径。
     *
     * @param fallback 未显式配置时使用的可信默认值
     * @return 按本方法契约生成的文本或标识
     */
    public String apiPathOr(String fallback) {
        return apiPath == null ? fallback : apiPath;
    }

    /**
     * 返回固定脱敏表示，避免调试输出或 JSON 适配泄露认证信息。
     *
     * @return 不含原始认证值的固定脱敏文本
     */
    @Override
    @JsonValue
    public String toString() {
        return "LLMProviderAccess[redacted]";
    }

    /**
     * 只接受无用户信息、查询或片段的 HTTP(S) 服务地址；空值交给供应商默认地址处理。
     *
     * @param value 待处理的值
     * @return 按本方法契约生成的文本或标识
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    private static @Nullable String validateBaseUrl(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            return value.replaceFirst("/+$", "");
        } catch (IllegalArgumentException ignored) {
            throw new LLMConfigurationException("Invalid LLM base-url in Spring configuration.");
        }
    }

    /**
     * 只接受站内绝对路径，拒绝外部地址、目录跳转和编码绕过；空值保留默认路径。
     *
     * @param value 待处理的值
     * @return 按本方法契约生成的文本或标识
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    private static @Nullable String validateApiPath(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var uri = URI.create(value);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !value.startsWith("/") || value.startsWith("//")
                    || !uri.normalize().equals(uri) || value.contains("%") || value.contains("\\")
                    || java.util.Arrays.stream(value.split("/")).anyMatch(part -> part.equals(".") || part.equals(".."))) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException ignored) {
            throw new LLMConfigurationException("Invalid LLM api-path in Spring configuration.");
        }
    }
}
