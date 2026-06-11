package org.zexnocs.teanekoagent.llm.instance.deepseek;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMChoice;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.time.Instant;
import java.util.*;

/**
 * DeepSeek 对话补全 API 与 LLM framework 之间的转换器。
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeepSeekChatCompletionMapper {
    /**
     * metadata 中写入 DeepSeek 请求体扩展字段的前缀。
     */
    public static final String EXTRA_BODY_PREFIX = "body.";

    /**
     * 将 LLM prompt 和 options 转换为 DeepSeek 请求数据。
     *
     * @param prompt LLM prompt
     * @param options LLM 调用参数
     * @param baseUrl DeepSeek API base URL
     * @param apiPath DeepSeek API path
     * @param apiKey DeepSeek API key
     * @return DeepSeek 请求数据
     */
    public static DeepSeekChatCompletionRequestData toRequest(ILLMPrompt prompt,
                                                              LLMModelOptions options,
                                                              String baseUrl,
                                                              String apiPath,
                                                              String apiKey) {
        var deepSeekOptions = DeepSeekModelOptions.copyOf(options);
        return DeepSeekChatCompletionRequestData.builder()
                .baseUrl(baseUrl)
                .apiPath(apiPath)
                .apiKey(apiKey)
                .model(deepSeekOptions.findModel().orElse(DeepSeekChatModel.DEFAULT_MODEL))
                .messages(toMessages(prompt.getMessages()))
                .thinking(toThinking(deepSeekOptions))
                .maxTokens(deepSeekOptions.findMaxTokens().orElse(null))
                .frequencyPenalty(deepSeekOptions.findFrequencyPenalty().orElse(null))
                .temperature(deepSeekOptions.findTemperature().orElse(null))
                .topP(deepSeekOptions.findTopP().orElse(null))
                .presencePenalty(deepSeekOptions.findPresencePenalty().orElse(null))
                .stop(nonEmptyList(deepSeekOptions.findStopWords().orElse(null)))
                .stream(deepSeekOptions.findStream().orElse(null))
                .responseFormat(toResponseFormat(deepSeekOptions))
                .tools(deepSeekOptions.findTools().map(DeepSeekChatCompletionMapper::toTools).orElse(null))
                .toolChoice(deepSeekOptions.findToolChoice().orElse(null))
                .logprobs(deepSeekOptions.findLogprobs().orElse(null))
                .topLogprobs(deepSeekOptions.findTopLogprobs().orElse(null))
                .streamOptions(toStreamOptions(deepSeekOptions))
                .userId(deepSeekOptions.findUserId().orElse(null))
                .extraBody(toExtraBody(deepSeekOptions.getMetadata()))
                .build();
    }

    /**
     * 将 DeepSeek 响应数据转换为统一 LLM result。
     *
     * @param response DeepSeek 响应数据
     * @return 统一 LLM result
     */
    public static LLMResult toResult(DeepSeekChatCompletionResponseData response) {
        return LLMResult.builder()
                .id(response.getId())
                .object(response.getObject())
                .created(response.getCreated() > 0 ? Instant.ofEpochSecond(response.getCreated()) : Instant.now())
                .model(response.getModel())
                .choices(toChoices(response.getChoices()))
                .usage(toUsage(response.getUsage()))
                .build();
    }

    /**
     * 转换消息列表。
     *
     * @param messages LLM 消息列表
     * @return DeepSeek 消息列表
     */
    private static List<Map<String, Object>> toMessages(List<ILLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var message : messages) {
            var item = new LinkedHashMap<String, Object>();
            item.put("role", message.getRole().getValue());
            putIfNotBlank(item, "name", message.getName());
            item.put("content", toContent(message.getContents()));
            if (message instanceof ILLMAssistantMessage assistantMessage) {
                var toolCalls = assistantMessage.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    item.put("tool_calls", toToolCalls(toolCalls));
                }
            }
            if (message instanceof ILLMToolMessage toolMessage) {
                putIfNotBlank(item, "tool_call_id", toolMessage.getToolCallId());
            }
            result.add(item);
        }
        return List.copyOf(result);
    }

    /**
     * 转换文本内容。
     *
     * @param contents LLM content 列表
     * @return DeepSeek 文本 content
     */
    private static String toContent(List<ILLMContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        var text = new StringBuilder();
        for (var content : contents) {
            if (content == null) {
                continue;
            } else {
                content.getContentPart();
            }
            if (content.getContentPart() instanceof TextLLMContentPart textPart) {
                appendContentText(text, textPart.getText());
            } else {
                appendContentText(text, content.getContentPart().toString());
            }
        }
        return text.toString();
    }

    /**
     * 转换 thinking 参数。
     *
     * @param options LLM 调用参数
     * @return DeepSeek thinking 请求对象
     */
    private static Map<String, Object> toThinking(DeepSeekModelOptions options) {
        var result = new LinkedHashMap<String, Object>();
        options.findThinking().ifPresent(thinking -> result.put("type", thinking ? "enabled" : "disabled"));
        options.findReasoningEffort().ifPresent(reasoningEffort -> result.put("reasoning_effort", reasoningEffort));
        return result.isEmpty() ? null : Map.copyOf(result);
    }

    /**
     * 转换 stream_options 参数。
     *
     * @param options DeepSeek 调用参数
     * @return DeepSeek stream_options 请求对象
     */
    private static Map<String, Object> toStreamOptions(DeepSeekModelOptions options) {
        if (!options.findStream().orElse(false)) {
            return null;
        }
        return options.findStreamIncludeUsage()
                .map(includeUsage -> Map.<String, Object>of("include_usage", includeUsage))
                .orElse(null);
    }

    /**
     * 转换响应格式。
     *
     * @param options LLM 调用参数
     * @return DeepSeek response_format 请求对象
     */
    private static Map<String, String> toResponseFormat(LLMModelOptions options) {
        return options.findResponseFormat()
                .filter(format -> format == LLMResponseFormat.JSON)
                .map(_ -> Map.of("type", "json_object"))
                .orElse(null);
    }

    /**
     * 转换工具定义列表。
     *
     * @param tools LLM 工具列表
     * @return DeepSeek tools 请求对象
     */
    private static List<Map<String, Object>> toTools(List<ILLMTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var tool : tools) {
            var function = new LinkedHashMap<String, Object>();
            putIfNotBlank(function, "name", tool.getName());
            putIfNotBlank(function, "description", tool.getDescription());
            function.put("parameters", toParameterSchema(tool.getParameters()));
            if (tool.isStrict()) {
                function.put("strict", true);
            }
            var item = new LinkedHashMap<String, Object>();
            item.put("type", firstNotBlank(tool.getType(), "function"));
            item.put("function", function);
            result.add(item);
        }
        return List.copyOf(result);
    }

    /**
     * 转换工具参数 schema。
     *
     * @param parameter LLM 工具参数
     * @return JSON Schema 风格参数对象
     */
    private static Map<String, Object> toParameterSchema(ILLMFunctionParameter parameter) {
        var schema = new LinkedHashMap<String, Object>();
        if (parameter == null) {
            schema.put("type", "object");
            schema.put("properties", Map.of());
            return schema;
        }
        putIfNotBlank(schema, "type", parameter.getType());
        putIfNotBlank(schema, "description", parameter.getDescription());
        if (parameter.getProperties() != null && !parameter.getProperties().isEmpty()) {
            var properties = new LinkedHashMap<String, Object>();
            parameter.getProperties().forEach((name, property) -> properties.put(name, toParameterSchema(property)));
            schema.put("properties", properties);
        }
        if (parameter.getRequired() != null && !parameter.getRequired().isEmpty()) {
            schema.put("required", parameter.getRequired());
        }
        if (parameter.getItems() != null) {
            schema.put("items", toParameterSchema(parameter.getItems()));
        }
        if (parameter.getEnumValues() != null && !parameter.getEnumValues().isEmpty()) {
            schema.put("enum", parameter.getEnumValues());
        }
        schema.put("additionalProperties", parameter.isAdditionalProperties());
        return schema;
    }

    /**
     * 转换 metadata 中的请求体扩展字段。
     *
     * @param metadata LLM metadata
     * @return 额外请求体字段
     */
    private static Map<String, Object> toExtraBody(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        metadata.forEach((key, value) -> {
            if (key != null && key.startsWith(EXTRA_BODY_PREFIX) && shouldWriteOptionalValue(value)) {
                result.put(key.substring(EXTRA_BODY_PREFIX.length()), value);
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 转换响应候选列表。
     *
     * @param choices DeepSeek 候选列表
     * @return 统一候选列表
     */
    private static List<ILLMChoice> toChoices(List<DeepSeekChatCompletionResponseData.Choice> choices) {
        if (choices == null || choices.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<ILLMChoice>();
        for (var choice : choices) {
            result.add(LLMChoice.builder()
                    .index(choice.getIndex())
                    .finishReason(choice.getFinishReason())
                    .message(toAssistantMessage(choice.getMessage()))
                    .logprobs(choice.getLogprobs() == null ? Map.of() : choice.getLogprobs())
                    .build());
        }
        return List.copyOf(result);
    }

    /**
     * 转换 assistant message。
     *
     * @param message DeepSeek assistant message
     * @return 统一 assistant message
     */
    private static LLMAssistantMessage toAssistantMessage(DeepSeekChatCompletionResponseData.Message message) {
        if (message == null) {
            return LLMAssistantMessage.builder()
                    .contents(List.of())
                    .toolCalls(List.of())
                    .build();
        }
        return LLMAssistantMessage.builder()
                .name(firstNotBlank(message.getName(), ""))
                .contents(LLMContentListBuilder.builder()
                        .addText(firstNotBlank(message.getContent(), ""))
                        .build())
                .toolCalls(toFrameworkToolCalls(message.getToolCalls()))
                .build();
    }

    /**
     * 转换响应中的工具调用。
     *
     * @param toolCalls DeepSeek 工具调用列表
     * @return 统一工具调用列表
     */
    private static List<ILLMToolCall> toFrameworkToolCalls(List<DeepSeekChatCompletionResponseData.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<ILLMToolCall>();
        for (var toolCall : toolCalls) {
            var function = toolCall.getFunction();
            result.add(LLMToolCall.builder()
                    .id(toolCall.getId())
                    .type(toolCall.getType())
                    .name(function == null ? null : function.getName())
                    .arguments(function == null ? null : function.getArguments())
                    .build());
        }
        return List.copyOf(result);
    }

    /**
     * 转换请求上下文中的工具调用。
     *
     * @param toolCalls LLM 工具调用列表
     * @return DeepSeek 工具调用对象列表
     */
    private static List<Map<String, Object>> toToolCalls(List<ILLMToolCall> toolCalls) {
        var result = new ArrayList<Map<String, Object>>();
        for (var toolCall : toolCalls) {
            var function = new LinkedHashMap<String, Object>();
            putIfNotBlank(function, "name", toolCall.getName());
            putIfNotBlank(function, "arguments", toolCall.getArguments());
            var item = new LinkedHashMap<String, Object>();
            putIfNotBlank(item, "id", toolCall.getId());
            putIfNotBlank(item, "type", toolCall.getType());
            item.put("function", function);
            result.add(item);
        }
        return List.copyOf(result);
    }

    /**
     * 转换 token 用量信息。
     *
     * @param usage DeepSeek token 用量
     * @return 统一 token 用量
     */
    private static LLMUsage toUsage(DeepSeekChatCompletionResponseData.Usage usage) {
        if (usage == null) {
            return LLMUsage.empty();
        }
        var completionDetails = usage.getCompletionTokensDetails();
        return LLMUsage.builder()
                .completionTokens(usage.getCompletionTokens())
                .promptTokens(usage.getPromptTokens())
                .totalTokens(usage.getTotalTokens())
                .promptCacheHitTokens(usage.getPromptCacheHitTokens())
                .promptCacheMissTokens(usage.getPromptCacheMissTokens())
                .reasoningTokens(completionDetails == null ? 0 : completionDetails.getReasoningTokens())
                .build();
    }

    /**
     * 向 map 中写入非空字符串字段。
     *
     * @param target 目标 map
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    /**
     * 返回非空列表；空列表作为未设置处理。
     *
     * @param list 待检查列表
     * @return 非空列表，或 {@code null}
     * @param <T> 列表元素类型
     */
    private static <T> List<T> nonEmptyList(List<T> list) {
        return list == null || list.isEmpty() ? null : list;
    }

    /**
     * 判断可选请求字段是否需要写入。
     *
     * @param value 字段值
     * @return 需要写入时返回 {@code true}
     */
    private static boolean shouldWriteOptionalValue(Object value) {
        return switch (value) {
            case null -> false;
            case String text -> !text.isBlank();
            case Collection<?> collection -> !collection.isEmpty();
            case Map<?, ?> map -> !map.isEmpty();
            default -> true;
        };
    }

    /**
     * 追加文本内容。
     *
     * @param target 文本构造器
     * @param value 待追加文本
     */
    private static void appendContentText(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(value);
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param primary 优先值
     * @param fallback 回退值
     * @return 第一个非空白字符串；没有则返回空字符串
     */
    private static String firstNotBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "";
    }
}
