package org.zexnocs.teanekoagent_old.personality.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Agent 人格配置字段检查器。
 * <br>用于在 ConfigData 字段写入前校验人格、模型和调用参数配置是否合理。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Component
@RequiredArgsConstructor
public class AgentPersonalityFieldChecker implements IConfigFieldChecker {
    /**
     * agent ID 允许的格式。
     */
    private static final Pattern AGENT_ID_PATTERN = Pattern.compile("[a-zA-Z0-9._-]{1,64}");

    /**
     * 普通 API 名称或 endpoint 允许的安全字符格式。
     */
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9._:/?&=%-]{1,512}");

    /**
     * LLM 模型注册服务。
     * <br>用于校验配置中的模型适配器 ID 是否已经注册。
     */
    private final ILLMModelService llmModelService;

    /**
     * 检查指定字段的配置值是否合法。
     *
     * @param field 字段名。
     * @param value 字段值。
     * @return 合法时返回 null，不合法时返回错误信息。
     */
    @Override
    public String isValid(String field, String value) {
        return switch (field) {
            case "agentId" -> checkAgentId(value);
            case "customPersonality" -> checkMaxLength(value, 6000, "customPersonality is too long");
            case "customSpeakingStyle" -> checkMaxLength(value, 2000, "customSpeakingStyle is too long");
            case "modelId" -> checkModelId(value);
            case "model" -> checkSafeShortText(value, 128, "model is not a valid model name");
            case "modelApi" -> checkModelApi(value);
            case "baseUrl" -> checkHttpUrl(value, "baseUrl must be an http/https URL");
            case "temperature" -> checkDoubleRange(value, 0, 2, "temperature must be between 0 and 2");
            case "topP" -> checkDoubleRange(value, 0, 1, "topP must be between 0 and 1");
            case "maxTokens" -> checkPositiveInt(value, 32768, "maxTokens must be a positive integer up to 32768");
            default -> null;
        };
    }

    /**
     * 校验 agent ID。
     *
     * @param value agent ID。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkAgentId(String value) {
        if (isBlank(value)) {
            return null;
        }
        return AGENT_ID_PATTERN.matcher(value.trim()).matches()
                ? null
                : "agentId can only contain letters, numbers, dot, underscore, and dash";
    }

    /**
     * 校验模型适配器 ID。
     *
     * @param value 模型适配器 ID。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkModelId(String value) {
        if (isBlank(value)) {
            return null;
        }
        final LLMModelId modelId;
        try {
            modelId = LLMModelId.of(value);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return llmModelService.getModels().containsKey(modelId)
                ? null
                : "LLM model id is not registered: " + value;
    }

    /**
     * 校验模型 API 配置。
     *
     * @param value API 名称或 endpoint。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkModelApi(String value) {
        if (isBlank(value)) {
            return null;
        }
        var trimmed = value.trim();
        if (looksLikeUrl(trimmed)) {
            return checkHttpUrl(trimmed, "modelApi URL must use http/https");
        }
        if (!SAFE_NAME_PATTERN.matcher(trimmed).matches()) {
            return "modelApi can only be a safe API name or http/https endpoint";
        }
        if (trimmed.toLowerCase().contains("key=") || trimmed.toLowerCase().contains("sk-")) {
            return "modelApi must not contain a raw API key";
        }
        return null;
    }

    /**
     * 校验 HTTP URL。
     *
     * @param value   URL 字符串。
     * @param message 校验失败时返回的错误信息。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkHttpUrl(String value, String message) {
        if (isBlank(value)) {
            return null;
        }
        try {
            var uri = new URI(value.trim());
            var scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return message;
            }
            return null;
        } catch (URISyntaxException e) {
            return message;
        }
    }

    /**
     * 校验 double 数值范围。
     *
     * @param value   原始字符串值。
     * @param min     最小值。
     * @param max     最大值。
     * @param message 校验失败时返回的错误信息。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkDoubleRange(String value, double min, double max, String message) {
        if (isBlank(value)) {
            return null;
        }
        try {
            var parsed = Double.parseDouble(value.trim());
            return parsed >= min && parsed <= max ? null : message;
        } catch (NumberFormatException e) {
            return message;
        }
    }

    /**
     * 校验正整数范围。
     *
     * @param value   原始字符串值。
     * @param max     最大值。
     * @param message 校验失败时返回的错误信息。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkPositiveInt(String value, int max, String message) {
        if (isBlank(value)) {
            return null;
        }
        try {
            var parsed = Integer.parseInt(value.trim());
            return parsed > 0 && parsed <= max ? null : message;
        } catch (NumberFormatException e) {
            return message;
        }
    }

    /**
     * 校验短文本字段。
     *
     * @param value     原始值。
     * @param maxLength 最大长度。
     * @param message   校验失败时返回的错误信息。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkSafeShortText(String value, int maxLength, String message) {
        if (isBlank(value)) {
            return null;
        }
        var trimmed = value.trim();
        if (trimmed.length() > maxLength || containsControlCharacter(trimmed)) {
            return message;
        }
        return null;
    }

    /**
     * 校验文本最大长度和控制字符。
     *
     * @param value     原始值。
     * @param maxLength 最大长度。
     * @param message   校验失败时返回的错误信息。
     * @return 合法时返回 null，否则返回错误信息。
     */
    private String checkMaxLength(String value, int maxLength, String message) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength && !containsControlCharacter(value) ? null : message;
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 原始值。
     * @return 如果为空白则返回 true。
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 判断字符串是否看起来像 URL。
     *
     * @param value 原始值。
     * @return 如果以 http 或 https 开头则返回 true。
     */
    private static boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    /**
     * 判断文本是否包含不允许的控制字符。
     *
     * @param value 原始文本。
     * @return 如果包含不允许的控制字符则返回 true。
     */
    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t');
    }
}
