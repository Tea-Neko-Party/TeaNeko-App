package org.zexnocs.teanekoagent.integration.monitor;

import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMTokenUsage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMToolMessage;
import org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 统一请求摘要与有界正文格式，不序列化任意供应商 metadata 或运行对象。
 * <br>2026/11/06：上下文、响应和公开推理字段集中输出；敏感值先脱敏，再转义、截断。
 * <br>2026/11/06：所有内容入口共用有界结构化脱敏，修复嵌套 JSON 和内联授权头遗漏。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.5-l
 * @version 4.8.5-l
 */
final class InteractionLogFormatter {
    private static final JsonMapper JSON = new JsonMapper();
    // 2026/11/06：跨字段共用处理预算，正文只在完整脱敏后进入长度截断。
    private final ObservationContentRedactor redactor = new ObservationContentRedactor();
    private final StringBuilder body = new StringBuilder();
    private final AgentObservationFileConfig settings;
    private final int maximum;
    private boolean truncated;

    /**
     * 每次汇总使用独立缓冲区，避免不同请求的正文混入。
     * @param settings 本次已应用配置
     */
    private InteractionLogFormatter(AgentObservationFileConfig settings) {
        this.settings = settings;
        maximum = Math.clamp(settings.getMaxContentCharacters(), 1024, 2_000_000);
    }

    /**
     * 按本次实际尝试计数，不读取共享父预算，也不将未知用量当零。
     * @param result 本次交互结果
     * @return 不含正文的摘要
     */
    static String metrics(LLMInteractionResult result) {
        long reported = 0;
        long estimated = 0;
        int unknown = 0;
        for (var attempt : result.attempts()) {
            var usage = attempt.tokenUsage().orElse(null);
            if (usage == null) {
                var supplied = attempt.usage().orElse(null);
                if (supplied == null) {
                    unknown++;
                } else {
                    reported += Math.max(supplied.getTotalTokens(),
                            (long) supplied.getPromptTokens() + supplied.getCompletionTokens());
                }
            } else if (usage.source() == LLMTokenUsage.Source.REPORTED) {
                reported += usage.tokens();
            } else {
                estimated += usage.tokens();
            }
        }
        return "，模型调用=" + result.attempts().size() + "，工具调用=" + result.tools().size()
                + "，已报告总token=" + reported + "，估算token=" + estimated + "，用量未知次数=" + unknown
                + (result.failure() == null ? "" : "，" + result.failure());
    }

    /**
     * 输出每个实际尝试的输入、响应及供应商提供的推理；不开启内容时不遍历消息。
     * @param result 原交互
     * @param settings 正文选择与上限
     * @return 单条日志附加正文，超过限制明确标记
     */
    static String details(LLMInteractionResult result, AgentObservationFileConfig settings) {
        if (!settings.isIncludeContext() && !settings.isIncludeReasoning()) {
            return "";
        }
        var out = new InteractionLogFormatter(settings);
        for (var attempt : result.attempts()) {
            if (out.truncated) {
                break;
            }
            out.add("模型尝试", Integer.toString(attempt.number()));
            if (settings.isIncludeContext()) {
                if (attempt.input().isEmpty()) {
                    out.add("输入", "未取得实际输入观察");
                } else {
                    var input = attempt.input().orElseThrow();
                    out.add("协议声明", input.declaration());
                    for (var message : input.messages()) {
                        if (out.truncated) {
                            break;
                        }
                        out.message("输入", message);
                    }
                }
            }
            var response = attempt.response().orElse(null);
            if (response == null) {
                out.add("响应", "未取得");
            } else {
                out.add("响应模型", response.getModel());
                response.getChoices().forEach(choice -> {
                    out.add("结束原因", choice.getFinishReason());
                    if (choice.getMessage() != null) {
                        out.message("响应", choice.getMessage());
                    }
                });
            }
        }
        if (settings.isIncludeContext() && !out.truncated) {
            result.tools().forEach(tool -> {
                out.add("工具结果", tool.call().getId() + "/" + tool.call().getName() + ":" + tool.status());
                out.add("工具输出", tool.output());
            });
        }
        return out.body + (out.truncated ? "；[正文已截断，上限=" + out.maximum + " 字符]" : "");
    }

    /**
     * 只投影受支持内容；图像及其他二进制不进入日志。
     * @param kind 输入或响应
     * @param message 实际统一消息
     */
    private void message(String kind, ILLMMessage message) {
        if (truncated) {
            return;
        }
        if (settings.isIncludeContext()) {
            add(kind + "角色", message.getRole().name());
            add("名称", message.getName());
            if (message instanceof ILLMToolMessage tool) {
                add("工具回填ID", tool.getToolCallId());
            }
            message.getContents().forEach(content -> add("内容",
                    content.getContentPart() instanceof TextLLMContentPart text ? text.getText() : "[非文本内容已隐藏]"));
            if (message instanceof ILLMAssistantMessage assistant) {
                assistant.getToolCalls().forEach(call -> {
                    add("工具调用", call.getId() + "/" + call.getName());
                    add("工具参数", call.getArguments());
                });
            }
        }
        if (!truncated && settings.isIncludeReasoning() && message instanceof ILLMAssistantMessage) {
            // 2026/11/06：各推理片段独立处理，避免拼接后破坏原本完整的 JSON 边界。
            reasoning(message.getProviderMetadata());
        }
    }

    /**
     * 只接受适配器已有的明文推理字段或 Responses summary；忽略加密状态和其他 metadata。
     * 2026/11/06：逐段脱敏并共用正文上限，保留结构化片段中的安全字段。
     * @param metadata 供应商透明字段
     */
    private void reasoning(Map<String, Object> metadata) {
        boolean provided = false;
        for (var entry : metadata.entrySet()) {
            if (truncated) {
                return;
            }
            if (entry.getKey().endsWith(".reasoningContent") && entry.getValue() instanceof String text && !text.isEmpty()) {
                add("供应商推理", text);
                provided = true;
            } else if (entry.getKey().endsWith(".responseInputItems") && entry.getValue() instanceof List<?> items) {
                for (var item : items) {
                    if (item instanceof Map<?, ?> fields && "reasoning".equals(fields.get("type"))
                            && fields.get("summary") instanceof List<?> summaries) {
                        for (var summary : summaries) {
                            if (summary instanceof Map<?, ?> part && part.get("text") instanceof String text && !text.isEmpty()) {
                                add("供应商推理", text);
                                provided = true;
                            }
                        }
                    }
                }
            }
        }
        if (!provided) {
            add("供应商推理", "未提供明文推理或摘要");
        }
    }

    /**
     * 清除敏感字段和链接后转义换行，保证一条请求只有一个 INFO 事件及一行文本。
     * @param label 固定字段名
     * @param value 实际字段值
     */
    private void add(String label, @org.jspecify.annotations.Nullable String value) {
        if (truncated || value == null || value.isEmpty()) {
            return;
        }
        // 2026/11/06：包含 JSON 编码字符串的工具结果、参数、输入和推理使用同一脱敏入口。
        var safe = redactor.redact(value);
        var chunk = "；" + label + "=" + JSON.writeValueAsString(safe);
        int remaining = maximum - body.length();
        if (chunk.length() > remaining) {
            body.append(chunk, 0, remaining);
            truncated = true;
        } else {
            body.append(chunk);
        }
    }
}
