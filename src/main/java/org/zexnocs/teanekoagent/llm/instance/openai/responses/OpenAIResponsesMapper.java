package org.zexnocs.teanekoagent.llm.instance.openai.responses;

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
import org.zexnocs.teanekoagent.llm.framework.tool.LLMFunctionParameterJsonMapper;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.time.Instant;
import java.util.*;

/**
 * OpenAI Responses API 与 TeaNeko LLM framework 之间的转换器。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenAIResponsesMapper {
    /** metadata 中写入 OpenAI 请求体扩展字段的前缀。 */
    public static final String EXTRA_BODY_PREFIX = "body.";

    /** assistant message 中保存 OpenAI reasoning input items 的 metadata key。 */
    public static final String RESPONSE_INPUT_ITEMS_METADATA = "openai.responseInputItems";

    /**
     * 将统一 Prompt 和 options 转换为 OpenAI Responses 请求。
     *
     * @param prompt LLM Prompt
     * @param options 模型参数
     * @param apiKey OpenAI API key
     * @return OpenAI Responses 请求数据
     */
    public static OpenAIResponsesRequestData toRequest(ILLMPrompt prompt,
                                                        LLMModelOptions options,
                                                        String apiKey) {
        var openAIOptions = OpenAIModelOptions.copyOf(options);
        var logprobsEnabled = openAIOptions.findLogprobs().orElse(false);
        return OpenAIResponsesRequestData.builder()
                .baseUrl(openAIOptions.findBaseUrl().orElse(OpenAIModelOptions.DEFAULT_BASE_URL))
                .apiPath(openAIOptions.findApiPath().orElse(OpenAIModelOptions.DEFAULT_API_PATH))
                .apiKey(apiKey)
                .organization(openAIOptions.findOrganization().orElse(null))
                .project(openAIOptions.findProject().orElse(null))
                .model(openAIOptions.findModel().orElse(OpenAIModelOptions.DEFAULT_MODEL))
                .input(toInput(prompt.getMessages()))
                .maxOutputTokens(openAIOptions.findMaxTokens().orElse(null))
                .reasoning(toReasoning(openAIOptions))
                .temperature(openAIOptions.findTemperature().orElse(null))
                .topP(openAIOptions.findTopP().orElse(null))
                .text(toText(openAIOptions))
                .tools(openAIOptions.findTools().map(OpenAIResponsesMapper::toTools).orElse(null))
                .toolChoice(openAIOptions.findToolChoice().orElse(null))
                .parallelToolCalls(openAIOptions.findParallelToolCalls().orElse(null))
                .include(toInclude(openAIOptions))
                .topLogprobs(logprobsEnabled
                        ? openAIOptions.findTopLogprobs().orElse(1)
                        : null)
                .store(openAIOptions.findStore().orElse(null))
                .promptCacheKey(openAIOptions.findPromptCacheKey().orElse(null))
                .safetyIdentifier(openAIOptions.findSafetyIdentifier().orElse(null))
                .serviceTier(openAIOptions.findServiceTier().orElse(null))
                .truncation(openAIOptions.findTruncation().orElse(null))
                .metadata(nonEmptyMap(openAIOptions.getRequestMetadata()))
                .extraBody(toExtraBody(openAIOptions.getMetadata()))
                .build();
    }

    /**
     * 将 OpenAI Responses 响应转换为统一 LLM Result。
     *
     * @param response OpenAI Responses 响应
     * @return 统一 LLM Result
     */
    public static LLMResult toResult(OpenAIResponsesResponseData response) {
        return LLMResult.builder()
                .id(response.getId())
                .object(firstNotBlank(response.getObject(), "response"))
                .created(response.getCreatedAt() > 0
                        ? Instant.ofEpochSecond(response.getCreatedAt())
                        : Instant.now())
                .model(response.getModel())
                .choices(toChoices(response))
                .usage(toUsage(response.getUsage()))
                .build();
    }

    /**
     * 将对话消息转换为 Responses API input items。
     *
     * @param messages LLM 消息列表
     * @return Responses input items
     */
    private static List<Map<String, Object>> toInput(List<ILLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var message : messages) {
            if (message instanceof ILLMToolMessage toolMessage) {
                result.add(toFunctionCallOutput(toolMessage));
                continue;
            }
            var content = toContent(message.getContents());
            if (!(message instanceof ILLMAssistantMessage assistantMessage)) {
                result.add(toInputMessage(message, content));
                continue;
            }
            result.addAll(providerInputItems(assistantMessage));
            var toolCalls = assistantMessage.getToolCalls();
            if (!content.isBlank() || toolCalls == null || toolCalls.isEmpty()) {
                result.add(toInputMessage(message, content));
            }
            if (toolCalls != null) {
                toolCalls.forEach(toolCall -> result.add(toFunctionCall(toolCall)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 读取 assistant message 中保存的 OpenAI 透明 input items。
     *
     * @param message assistant message
     * @return 可回传给 Responses API 的 input items
     */
    private static List<Map<String, Object>> providerInputItems(ILLMAssistantMessage message) {
        var metadata = message.getProviderMetadata();
        if (metadata == null || !(metadata.get(RESPONSE_INPUT_ITEMS_METADATA) instanceof List<?> items)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key.toString(), value);
                }
            });
            if (!copy.isEmpty()) {
                result.add(Map.copyOf(copy));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 转换普通 message input item。
     *
     * @param message LLM message
     * @param content 文本正文
     * @return OpenAI message input item
     */
    private static Map<String, Object> toInputMessage(ILLMMessage message, String content) {
        var item = new LinkedHashMap<String, Object>();
        item.put("type", "message");
        item.put("role", message.getRole().getValue());
        item.put("content", content);
        return item;
    }

    /**
     * 转换历史 Function Call input item。
     *
     * @param toolCall 工具调用
     * @return OpenAI Function Call item
     */
    private static Map<String, Object> toFunctionCall(ILLMToolCall toolCall) {
        var item = new LinkedHashMap<String, Object>();
        item.put("type", "function_call");
        putIfNotBlank(item, "call_id", toolCall.getId());
        putIfNotBlank(item, "name", toolCall.getName());
        item.put("arguments", firstNotBlank(toolCall.getArguments(), "{}"));
        return item;
    }

    /**
     * 转换工具执行结果 input item。
     *
     * @param toolMessage 工具结果消息
     * @return OpenAI Function Call Output item
     */
    private static Map<String, Object> toFunctionCallOutput(ILLMToolMessage toolMessage) {
        var item = new LinkedHashMap<String, Object>();
        item.put("type", "function_call_output");
        putIfNotBlank(item, "call_id", toolMessage.getToolCallId());
        item.put("output", toContent(toolMessage.getContents()));
        return item;
    }

    /**
     * 将 LLM Content 列表合并为 Responses 文本正文。
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
     * 转换 reasoning 配置。
     *
     * @param options OpenAI 参数
     * @return reasoning 请求对象或 {@code null}
     */
    private static Map<String, Object> toReasoning(OpenAIModelOptions options) {
        var result = new LinkedHashMap<String, Object>();
        options.findThinking().ifPresent(thinking -> result.put("effort", thinking
                ? options.findReasoningEffort().orElse("medium")
                : "none"));
        if (!result.containsKey("effort")) {
            options.findReasoningEffort().ifPresent(value -> result.put("effort", value));
        }
        options.findReasoningSummary().ifPresent(value -> result.put("summary", value));
        return result.isEmpty() ? null : Map.copyOf(result);
    }

    /**
     * 转换文本格式和 verbosity 配置。
     *
     * @param options OpenAI 参数
     * @return text 请求对象或 {@code null}
     */
    private static Map<String, Object> toText(OpenAIModelOptions options) {
        var result = new LinkedHashMap<String, Object>();
        if (options.findResponseFormat().orElse(LLMResponseFormat.TEXT) == LLMResponseFormat.JSON) {
            result.put("format", Map.of("type", "json_object"));
        }
        options.findVerbosity().ifPresent(value -> result.put("verbosity", value));
        return result.isEmpty() ? null : Map.copyOf(result);
    }

    /**
     * 构造额外响应字段列表。
     *
     * @param options OpenAI 参数
     * @return include 字段或 {@code null}
     */
    private static List<String> toInclude(OpenAIModelOptions options) {
        return options.findStore()
                .filter(store -> !store)
                .map(_ -> List.of("reasoning.encrypted_content"))
                .orElse(null);
    }

    /**
     * 转换 Responses API 扁平 Function Tool 定义。
     *
     * @param tools LLM 工具列表
     * @return OpenAI tools 列表
     */
    private static List<Map<String, Object>> toTools(List<ILLMTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var tool : tools) {
            var item = new LinkedHashMap<String, Object>();
            item.put("type", "function");
            putIfNotBlank(item, "name", tool.getName());
            putIfNotBlank(item, "description", tool.getDescription());
            item.put("parameters", LLMFunctionParameterJsonMapper.toJsonSchema(tool.getParameters()));
            item.put("strict", tool.isStrict());
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
            if (key != null && key.startsWith(EXTRA_BODY_PREFIX) && shouldWriteOptionalValue(value)) {
                result.put(key.substring(EXTRA_BODY_PREFIX.length()), value);
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 聚合 Responses output items 为统一候选结果。
     *
     * @param response OpenAI 响应
     * @return 统一候选列表
     */
    private static List<ILLMChoice> toChoices(OpenAIResponsesResponseData response) {
        var text = new StringBuilder();
        var toolCalls = new ArrayList<ILLMToolCall>();
        var logprobs = new ArrayList<Map<String, Object>>();
        var providerInputItems = new ArrayList<Map<String, Object>>();
        var output = response.getOutput();
        if (output != null) {
            for (var item : output) {
                if (item == null) {
                    continue;
                }
                if ("function_call".equals(item.getType())) {
                    toolCalls.add(LLMToolCall.builder()
                            .id(item.getCallId())
                            .type("function")
                            .name(item.getName())
                            .arguments(item.getArguments())
                            .build());
                    continue;
                }
                if ("reasoning".equals(item.getType())) {
                    providerInputItems.add(toReasoningInputItem(item));
                    continue;
                }
                if (!"message".equals(item.getType()) || item.getContent() == null) {
                    continue;
                }
                for (var content : item.getContent()) {
                    if (content == null) {
                        continue;
                    }
                    appendText(text, "refusal".equals(content.getType())
                            ? content.getRefusal()
                            : content.getText());
                    if (content.getLogprobs() != null) {
                        logprobs.addAll(content.getLogprobs());
                    }
                }
            }
        }
        var message = LLMAssistantMessage.builder()
                .contents(LLMContentListBuilder.builder().addText(text.toString()).build())
                .toolCalls(List.copyOf(toolCalls))
                .providerMetadata(providerInputItems.isEmpty()
                        ? Map.of()
                        : Map.of(RESPONSE_INPUT_ITEMS_METADATA, List.copyOf(providerInputItems)))
                .build();
        var choice = LLMChoice.builder()
                .index(0)
                .finishReason(toFinishReason(response, toolCalls))
                .message(message)
                .logprobs(logprobs.isEmpty() ? Map.of() : Map.of("output_text", List.copyOf(logprobs)))
                .build();
        return List.of(choice);
    }

    /**
     * 将 reasoning 输出转换为下一轮可回传的透明 input item。
     *
     * @param item reasoning 输出 item
     * @return reasoning input item
     */
    private static Map<String, Object> toReasoningInputItem(OpenAIResponsesResponseData.OutputItem item) {
        var result = new LinkedHashMap<String, Object>();
        result.put("type", "reasoning");
        putIfNotBlank(result, "id", item.getId());
        if (item.getSummary() != null) {
            result.put("summary", item.getSummary());
        }
        putIfNotBlank(result, "encrypted_content", item.getEncryptedContent());
        return Map.copyOf(result);
    }

    /**
     * 根据响应状态和工具调用计算统一结束原因。
     *
     * @param response OpenAI 响应
     * @param toolCalls 工具调用列表
     * @return 统一结束原因
     */
    private static String toFinishReason(OpenAIResponsesResponseData response,
                                         List<ILLMToolCall> toolCalls) {
        if (!toolCalls.isEmpty()) {
            return "tool_calls";
        }
        if (response.getIncompleteDetails() != null
                && response.getIncompleteDetails().getReason() != null) {
            return response.getIncompleteDetails().getReason();
        }
        return switch (firstNotBlank(response.getStatus(), "completed")) {
            case "completed" -> "stop";
            case "incomplete" -> "length";
            default -> response.getStatus();
        };
    }

    /**
     * 转换 OpenAI token usage。
     *
     * @param usage OpenAI usage
     * @return 统一 token usage
     */
    private static LLMUsage toUsage(OpenAIResponsesResponseData.Usage usage) {
        if (usage == null) {
            return LLMUsage.empty();
        }
        var cachedTokens = usage.getInputTokensDetails() == null
                ? 0
                : usage.getInputTokensDetails().getCachedTokens();
        var reasoningTokens = usage.getOutputTokensDetails() == null
                ? 0
                : usage.getOutputTokensDetails().getReasoningTokens();
        return LLMUsage.builder()
                .promptTokens(usage.getInputTokens())
                .completionTokens(usage.getOutputTokens())
                .totalTokens(usage.getTotalTokens())
                .promptCacheHitTokens(cachedTokens)
                .promptCacheMissTokens(Math.max(0, usage.getInputTokens() - cachedTokens))
                .reasoningTokens(reasoningTokens)
                .build();
    }

    /**
     * 将空 map 规范化为 {@code null}，使可选字段不参与序列化。
     *
     * @param value 待检查 map
     * @return 非空 map 副本或 {@code null}
     * @param <K> key 类型
     * @param <V> value 类型
     */
    private static <K, V> Map<K, V> nonEmptyMap(Map<K, V> value) {
        return value == null || value.isEmpty() ? null : Map.copyOf(value);
    }

    /**
     * 判断额外请求字段是否包含有效值。
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
     * 使用换行符追加非空文本。
     *
     * @param target 文本构造器
     * @param value 待追加文本
     */
    private static void appendText(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append('\n');
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
