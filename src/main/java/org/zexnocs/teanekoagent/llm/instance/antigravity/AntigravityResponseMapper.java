package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 将原生终态和供应商用量映射为统一交互结果，不把拒答或截断误报为成功。
 * <br>2026/11/10：思考 token 计入生成用量，缺失关键用量字段时保留未知。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class AntigravityResponseMapper {
    /** 纯转换器不持有供应商会话。 */
    private AntigravityResponseMapper() { }

    /**
     * @param source 原生响应
     * @param mapper JSON 转换器
     * @return 真实模型标识、候选终态及用量
     * @throws IllegalArgumentException 当成功候选或用量不符合协议时
     */
    public static ILLMResult toResult(AntigravityResponseData source, ObjectMapper mapper) {
        var choices = new ArrayList<ILLMChoice>();
        if (source.candidates() != null) {
            for (var candidate : source.candidates()) {
                String finish = finish(candidate.path("finishReason").asString(""));
                var message = "stop".equals(finish)
                        ? AntigravityMessages.decode(candidate.path("content").path("parts"), mapper)
                        : LLMAssistantMessage.builder().contents(List.of()).build();
                if ("stop".equals(finish) && !message.getToolCalls().isEmpty()) {
                    finish = "tool_calls";
                }
                int index = candidate.has("index") ? number(candidate, "index") : choices.size();
                choices.add(LLMChoice.builder().index(index).finishReason(finish).message(message).build());
            }
        }
        if (choices.isEmpty() && source.promptFeedback() != null
                && source.promptFeedback().hasNonNull("blockReason")) {
            String reason = source.promptFeedback().path("blockReason").asString("");
            if (!reason.isBlank() && !"BLOCK_REASON_UNSPECIFIED".equals(reason)) {
                choices.add(LLMChoice.builder().index(0).finishReason("content_filter")
                        .message(LLMAssistantMessage.builder().contents(List.of()).build()).build());
            }
        }
        return LLMResult.builder().id(source.responseId()).model(source.modelVersion())
                .choices(List.copyOf(choices)).usage(usage(source.usageMetadata())).build();
    }

    /**
     * @param reason 原生结束标记，不含正文
     * @return 统一协议的明确终态，未知原因不降级为成功
     */
    private static String finish(String reason) {
        return switch (reason) {
            case "STOP" -> "stop";
            case "MAX_TOKENS" -> "length";
            case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY",
                    "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION" -> "content_filter";
            default -> "error";
        };
    }

    /**
     * @param raw 原生用量，允许缺失
     * @return 供应商实际报告的用量，关键字段缺失时为空
     * @throws IllegalArgumentException 当用量为负或缓存计数大于输入时
     */
    private static @Nullable LLMUsage usage(@Nullable JsonNode raw) {
        if (raw == null || !raw.isObject()
                || !raw.propertyNames().containsAll(Set.of("promptTokenCount", "candidatesTokenCount", "totalTokenCount"))) {
            return null;
        }
        int prompt = number(raw, "promptTokenCount");
        int candidate = number(raw, "candidatesTokenCount");
        int total = number(raw, "totalTokenCount");
        int thoughts = raw.has("thoughtsTokenCount") ? number(raw, "thoughtsTokenCount") : 0;
        int cached = raw.has("cachedContentTokenCount") ? number(raw, "cachedContentTokenCount") : 0;
        if (cached > prompt || (long) candidate + thoughts > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("原生模型用量计数关系不合法。");
        }
        return LLMUsage.builder().promptTokens(prompt).completionTokens(candidate + thoughts).totalTokens(total)
                .reasoningTokens(thoughts).promptCacheHitTokens(cached).promptCacheMissTokens(prompt - cached).build();
    }

    /**
     * @param object 原生对象
     * @param name 代码固定的计数字段
     * @return 非负且可由统一类型表示的整数
     * @throws IllegalArgumentException 当计数类型或范围无效时
     */
    private static int number(JsonNode object, String name) {
        var value = object.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalArgumentException("原生响应计数字段要求非负整数：" + name);
        }
        return value.intValue();
    }
}
