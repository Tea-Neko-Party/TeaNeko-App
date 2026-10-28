package org.zexnocs.teanekoagent.llm.instance.openai.responses;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
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
import org.zexnocs.teanekoagent.llm.instance.LLMRequestBodyExtensions;
import org.zexnocs.teanekoagent.llm.instance.openai.OpenAIContentMapper;

import java.time.Instant;
import java.util.*;

/**
 * OpenAI Responses API 与 TeaNeko LLM framework 之间的转换器。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.1
 * @version 4.5.0-l
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
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param options 模型参数
     * @param apiKey 仅供认证边界使用的本地凭据，禁止进入日志或普通消息
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
                .extraBody(LLMRequestBodyExtensions.fromMetadata(openAIOptions.getMetadata(), OpenAIResponsesRequestData.class))
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
     * @return 保持原顺序、工具配对及透明状态的 Responses 输入项
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
            var content = OpenAIContentMapper.toResponsesContent(message);
            if (!(message instanceof ILLMAssistantMessage assistantMessage)) {
                result.add(toInputMessage(message, content));
                continue;
            }
            result.addAll(providerInputItems(assistantMessage));
            var toolCalls = assistantMessage.getToolCalls();
            if (!(content instanceof String text) || !text.isBlank() || toolCalls == null || toolCalls.isEmpty()) {
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
     * @param message 需要发送或映射的消息
     * @return 可回传给 Responses API 的 input items
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private static List<Map<String, Object>> providerInputItems(ILLMAssistantMessage message) {
        var metadata = message.getProviderMetadata();
        if (metadata == null || !(metadata.get(RESPONSE_INPUT_ITEMS_METADATA) instanceof List<?> items)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var item : items) {
            if (!(item instanceof Map<?, ?> map) || !"reasoning".equals(map.get("type"))
                    || !Set.of("type", "id", "summary", "encrypted_content", "status").containsAll(map.keySet())) {
                throw new IllegalArgumentException("Unsupported opaque Responses state; use explicit messages and tool calls.");
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
     * @param message 需要发送或映射的消息
     * @param content 文本正文
     * @return 由统一消息映射的 Responses 消息输入项
     */
    private static Map<String, Object> toInputMessage(ILLMMessage message, Object content) {
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
     * @return 保留调用 ID、名称与参数的函数调用输入项
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
     * @return 与原函数调用 ID 配对的工具结果输入项
     */
    private static Map<String, Object> toFunctionCallOutput(ILLMToolMessage toolMessage) {
        var item = new LinkedHashMap<String, Object>();
        item.put("type", "function_call_output");
        putIfNotBlank(item, "call_id", toolMessage.getToolCallId());
        item.put("output", OpenAIContentMapper.toResponsesContent(toolMessage));
        return item;
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
     * 聚合 Responses 输出项为统一候选，保留拒绝语义且不将推理状态并入可见正文。
     *
     * @param response OpenAI 响应
     * @return 统一候选列表
     */
    private static List<ILLMChoice> toChoices(OpenAIResponsesResponseData response) {
        var text = new StringBuilder();
        var toolCalls = new ArrayList<ILLMToolCall>();
        var logprobs = new ArrayList<Map<String, Object>>();
        var providerInputItems = new ArrayList<Map<String, Object>>();
        boolean refused = false;
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
                    refused |= "refusal".equals(content.getType());
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
                .finishReason(refused ? "refusal" : toFinishReason(response, toolCalls))
                .message(message)
                .logprobs(logprobs.isEmpty() ? Map.of() : Map.of("output_text", List.copyOf(logprobs)))
                .build();
        return List.of(choice);
    }

    /**
     * 将 reasoning 输出转换为下一轮可回传的透明 input item。
     *
     * @param item reasoning 输出 item
     * @return 仅含受支持透明字段的推理状态输入项
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
     * 根据响应状态和工具调用计算统一结束原因，未完整或失败的响应不能开放工具副作用。
     *
     * @param response OpenAI 响应
     * @param toolCalls 工具调用列表
     * @return 统一结束原因
     */
    private static String toFinishReason(OpenAIResponsesResponseData response,
                                         List<ILLMToolCall> toolCalls) {
        if (response.getIncompleteDetails() != null
                && response.getIncompleteDetails().getReason() != null) {
            return response.getIncompleteDetails().getReason();
        }
        return switch (firstNotBlank(response.getStatus(), "completed")) {
            case "completed" -> toolCalls.isEmpty() ? "stop" : "tool_calls";
            case "incomplete" -> "length";
            default -> response.getStatus();
        };
    }

    /**
     * 转换 OpenAI token usage。
     *
     * @param usage 用于本次处理的 {@code usage} 数据
     * @return 统一 token usage，供应商未提供时为 null
     */
    private static LLMUsage toUsage(OpenAIResponsesResponseData.Usage usage) {
        if (usage == null) {
            return null;
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
     * @param <K> key 类型
     * @param <V> value 类型
     * @return 非空 map 副本或 {@code null}
     */
    private static <K, V> Map<K, V> nonEmptyMap(Map<K, V> value) {
        return value == null || value.isEmpty() ? null : Map.copyOf(value);
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
