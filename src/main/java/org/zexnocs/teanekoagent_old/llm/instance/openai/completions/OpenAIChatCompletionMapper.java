package org.zexnocs.teanekoagent_old.llm.instance.openai.completions;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoagent_old.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMToolMessage;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent_old.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent_old.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent_old.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent_old.llm.framework.response.interfaces.ILLMChoice;
import org.zexnocs.teanekoagent_old.llm.framework.tool.LLMFunctionParameterJsonMapper;
import org.zexnocs.teanekoagent_old.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolCall;

import java.time.Instant;
import java.util.*;

/**
 * OpenAI Chat Completions 兼容 API 与 LLM framework 之间的转换器。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenAIChatCompletionMapper {
    /** assistant message 中保存兼容供应商 reasoning_content 的 metadata key。 */
    public static final String REASONING_CONTENT_METADATA = "openai.chat.reasoningContent";

    /**
     * 将统一 Prompt 和 options 转换为 Chat Completions 请求。
     *
     * @param prompt LLM Prompt
     * @param options 模型参数
     * @param apiKey API key
     * @return Chat Completions 请求数据
     */
    public static OpenAIChatCompletionRequestData toRequest(ILLMPrompt prompt,
                                                             OpenAIChatCompletionModelOptions options,
                                                             String apiKey) {
        return OpenAIChatCompletionRequestData.builder()
                .baseUrl(options.findBaseUrl().orElse(OpenAIChatCompletionModelOptions.DEFAULT_BASE_URL))
                .apiPath(options.findApiPath().orElse(OpenAIChatCompletionModelOptions.DEFAULT_API_PATH))
                .apiKey(apiKey)
                .organization(options.findOrganization().orElse(null))
                .project(options.findProject().orElse(null))
                .extraHeaders(toExtraHeaders(options.getMetadata()))
                .model(options.findModel().orElse(OpenAIChatCompletionModelOptions.DEFAULT_MODEL))
                .messages(toMessages(prompt.getMessages()))
                .maxCompletionTokens(options.findMaxTokens().orElse(null))
                .temperature(options.findTemperature().orElse(null))
                .topP(options.findTopP().orElse(null))
                .frequencyPenalty(options.findFrequencyPenalty().orElse(null))
                .presencePenalty(options.findPresencePenalty().orElse(null))
                .stop(nonEmptyList(options.findStopWords().orElse(null)))
                .stream(options.findStream().orElse(null))
                .responseFormat(toResponseFormat(options))
                .tools(options.findTools().map(OpenAIChatCompletionMapper::toTools).orElse(null))
                .toolChoice(options.findToolChoice().orElse(null))
                .parallelToolCalls(options.findParallelToolCalls().orElse(null))
                .logprobs(options.findLogprobs().orElse(null))
                .topLogprobs(options.findTopLogprobs().orElse(null))
                .extraBody(toExtraBody(options.getMetadata()))
                .build();
    }

    /**
     * 将 Chat Completions 响应转换为统一 LLM Result。
     *
     * @param response Chat Completions 响应
     * @return 统一 LLM Result
     */
    public static LLMResult toResult(OpenAIChatCompletionResponseData response) {
        return LLMResult.builder()
                .id(response.getId())
                .object(firstNotBlank(response.getObject(), "chat.completion"))
                .created(response.getCreated() > 0
                        ? Instant.ofEpochSecond(response.getCreated())
                        : Instant.now())
                .model(response.getModel())
                .choices(toChoices(response.getChoices()))
                .usage(toUsage(response.getUsage()))
                .build();
    }

    /**
     * 转换 OpenAI Chat Completions 风格消息列表。
     *
     * @param messages LLM 消息列表
     * @return Chat Completions 消息列表
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
            if (message instanceof ILLMAssistantMessage assistantMessage) {
                putIfNotBlank(item, "reasoning_content", reasoningContent(assistantMessage));
                var toolCalls = assistantMessage.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    item.put("tool_calls", toToolCalls(toolCalls));
                }
            }
            if (message instanceof ILLMToolMessage toolMessage) {
                putIfNotBlank(item, "tool_call_id", toolMessage.getToolCallId());
            }
            item.put("content", toContent(message.getContents()));
            result.add(item);
        }
        return List.copyOf(result);
    }

    /**
     * 读取 assistant message 中保存的兼容供应商 reasoning_content。
     *
     * @param message assistant message
     * @return reasoning_content 或空字符串
     */
    private static String reasoningContent(ILLMAssistantMessage message) {
        var metadata = message.getProviderMetadata();
        if (metadata == null || metadata.get(REASONING_CONTENT_METADATA) == null) {
            return "";
        }
        return metadata.get(REASONING_CONTENT_METADATA).toString();
    }

    /**
     * 将 LLM Content 列表合并为文本正文。
     *
     * @param contents LLM Content 列表
     * @return 合并后的文本
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
                appendText(text, textPart.getText());
            } else {
                appendText(text, content.getContentPart().toString());
            }
        }
        return text.toString();
    }

    /**
     * 转换 JSON 输出格式。
     *
     * @param options Chat Completions 参数
     * @return response_format 请求对象或 {@code null}
     */
    private static Map<String, String> toResponseFormat(OpenAIChatCompletionModelOptions options) {
        return options.findResponseFormat()
                .filter(format -> format == LLMResponseFormat.JSON)
                .map(_ -> Map.of("type", "json_object"))
                .orElse(null);
    }

    /**
     * 转换 OpenAI Chat Completions 风格 Function Tool 定义。
     *
     * @param tools LLM 工具列表
     * @return Chat Completions tools 列表
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
            function.put("parameters", LLMFunctionParameterJsonMapper.toJsonSchema(tool.getParameters()));
            function.put("strict", tool.isStrict());
            result.add(Map.of("type", "function", "function", Map.copyOf(function)));
        }
        return List.copyOf(result);
    }

    /**
     * 转换历史工具调用。
     *
     * @param toolCalls 工具调用列表
     * @return Chat Completions tool_calls 列表
     */
    private static List<Map<String, Object>> toToolCalls(List<ILLMToolCall> toolCalls) {
        var result = new ArrayList<Map<String, Object>>();
        for (var toolCall : toolCalls) {
            var function = new LinkedHashMap<String, Object>();
            putIfNotBlank(function, "name", toolCall.getName());
            function.put("arguments", firstNotBlank(toolCall.getArguments(), "{}"));
            var item = new LinkedHashMap<String, Object>();
            putIfNotBlank(item, "id", toolCall.getId());
            item.put("type", "function");
            item.put("function", function);
            result.add(item);
        }
        return List.copyOf(result);
    }

    /**
     * 提取 metadata 中以 {@code body.} 开头的额外请求字段。
     *
     * @param metadata 模型 metadata
     * @return 额外请求字段
     */
    private static Map<String, Object> toExtraBody(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        metadata.forEach((key, value) -> {
            if (key != null
                    && key.startsWith(OpenAIChatCompletionModelOptions.EXTRA_BODY_PREFIX)
                    && shouldWriteOptionalValue(value)) {
                result.put(key.substring(OpenAIChatCompletionModelOptions.EXTRA_BODY_PREFIX.length()), value);
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 提取 metadata 中以 {@code header.} 开头的额外请求头。
     *
     * @param metadata 模型 metadata
     * @return 额外请求头
     */
    private static Map<String, String> toExtraHeaders(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, String>();
        metadata.forEach((key, value) -> {
            if (key != null
                    && key.startsWith(OpenAIChatCompletionModelOptions.EXTRA_HEADER_PREFIX)
                    && shouldWriteOptionalValue(value)) {
                result.put(key.substring(OpenAIChatCompletionModelOptions.EXTRA_HEADER_PREFIX.length()), value.toString());
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 转换 Chat Completions 响应候选列表。
     *
     * @param choices Chat Completions 候选列表
     * @return 统一候选列表
     */
    private static List<ILLMChoice> toChoices(List<OpenAIChatCompletionResponseData.Choice> choices) {
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
     * 转换 Chat Completions assistant 消息。
     *
     * @param message Chat Completions assistant 消息
     * @return 统一 assistant 消息
     */
    private static LLMAssistantMessage toAssistantMessage(OpenAIChatCompletionResponseData.Message message) {
        if (message == null) {
            return LLMAssistantMessage.builder()
                    .contents(List.of())
                    .toolCalls(List.of())
                    .build();
        }
        var providerMetadata = new LinkedHashMap<String, Object>();
        if (message.getReasoningContent() != null && !message.getReasoningContent().isBlank()) {
            providerMetadata.put(REASONING_CONTENT_METADATA, message.getReasoningContent());
        }
        return LLMAssistantMessage.builder()
                .name(firstNotBlank(message.getName(), ""))
                .contents(LLMContentListBuilder.builder()
                        .addText(firstNotBlank(message.getContent(), ""))
                        .build())
                .toolCalls(toFrameworkToolCalls(message.getToolCalls()))
                .providerMetadata(Map.copyOf(providerMetadata))
                .build();
    }

    /**
     * 转换 Chat Completions 工具调用列表。
     *
     * @param toolCalls Chat Completions 工具调用列表
     * @return 统一工具调用列表
     */
    private static List<ILLMToolCall> toFrameworkToolCalls(List<OpenAIChatCompletionResponseData.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<ILLMToolCall>();
        for (var toolCall : toolCalls) {
            var function = toolCall.getFunction();
            result.add(LLMToolCall.builder()
                    .id(toolCall.getId())
                    .type(firstNotBlank(toolCall.getType(), "function"))
                    .name(function == null ? null : function.getName())
                    .arguments(function == null ? null : function.getArguments())
                    .build());
        }
        return List.copyOf(result);
    }

    /**
     * 转换 Chat Completions token usage。
     *
     * @param usage Chat Completions usage
     * @return 统一 token usage
     */
    private static LLMUsage toUsage(OpenAIChatCompletionResponseData.Usage usage) {
        if (usage == null) {
            return LLMUsage.empty();
        }
        var cachedTokens = usage.getPromptTokenDetails() == null
                ? 0
                : usage.getPromptTokenDetails().getCachedTokens();
        var reasoningTokens = usage.getCompletionTokenDetails() == null
                ? 0
                : usage.getCompletionTokenDetails().getReasoningTokens();
        return LLMUsage.builder()
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .promptCacheHitTokens(cachedTokens)
                .promptCacheMissTokens(Math.max(0, usage.getPromptTokens() - cachedTokens))
                .reasoningTokens(reasoningTokens)
                .build();
    }

    /**
     * 返回非空列表，空列表按未设置处理。
     *
     * @param value 待检查列表
     * @return 非空列表或 {@code null}
     * @param <T> 列表元素类型
     */
    private static <T> List<T> nonEmptyList(List<T> value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * 判断可选字段是否需要写入请求。
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
     * 追加非空文本。
     *
     * @param target 文本构造器
     * @param value 待追加文本
     */
    private static void appendText(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(value);
    }

    /**
     * 向 map 写入非空白字符串。
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
     * 返回第一个非空白字符串。
     *
     * @param primary 优先值
     * @param fallback 回退值
     * @return 非空白字符串或空字符串
     */
    private static String firstNotBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }
}
