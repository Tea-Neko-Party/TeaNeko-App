package org.zexnocs.teanekoagent.integration.monitor;

import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMTokenUsage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonInstruction;
import org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 统一请求摘要与有界正文格式，不序列化任意供应商 metadata 或运行对象。
 * <br>2026/11/06：上下文、响应和公开推理字段集中输出；敏感值先脱敏，再转义、截断。
 * <br>2026/11/06：所有内容入口共用有界结构化脱敏，修复嵌套 JSON 和内联授权头遗漏。
 * <br>2026/11/06：按独立开关和日志级别输出精简文字汇总，去重并继续脱敏。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.5-l
 * @version 4.8.6-l
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
        var models = new java.util.LinkedHashSet<String>();
        var metricRedactor = new ObservationContentRedactor();
        for (var attempt : result.attempts()) {
            attempt.response().ifPresent(response -> {
                if (response.getModel() != null && !response.getModel().isBlank()) {
                    var model = metricRedactor.redact(response.getModel()).replace('\n', ' ').replace('\r', ' ');
                    models.add(model.substring(0, Math.min(model.length(), 128)));
                }
            });
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
                + "，响应模型=" + (models.isEmpty() ? "未取得" : String.join(" / ", models))
                + "，已报告总token=" + reported + "，估算token=" + estimated + "，用量未知次数=" + unknown
                + (result.failure() == null ? "" : "，" + result.failure());
    }

    /**
     * 2026/11/06：按稳定分组输出一次输入及最终回复，诊断项独立选择并按原文去重。
     * @param result 原交互
     * @param settings 正文选择与上限
     * @return 一条日志事件的附加正文
     */
    static String details(LLMInteractionResult result, AgentObservationFileConfig settings) {
        var out = new InteractionLogFormatter(settings);
        if (settings.isIncludeContext()) {
            if (settings.isIncludeUser()) {
                result.attempts().stream().flatMap(attempt -> attempt.input().stream()).findFirst().flatMap(input -> input.messages().reversed().stream()
                        .filter(message -> message.getRole()
                                == org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.LLMMessageRole.USER)
                        .findFirst()).ifPresent(message -> out.message("本轮用户输入", message));
            }
            if (settings.isIncludeFinalOutput()) {
                result.finalCandidate().ifPresent(message -> out.message("最终回复", message));
            }
            var systems = new java.util.LinkedHashSet<String>();
            var declarations = new java.util.LinkedHashSet<String>();
            for (var attempt : result.attempts()) {
                attempt.input().ifPresent(input -> {
                    if (settings.isIncludeSchema()) {
                        out.protocol(input.declaration(), declarations);
                    }
                    for (var message : input.messages()) {
                        if (message instanceof LLMJsonInstruction (
                                String guidance, String schema, boolean nativeSchema
                        )) {
                            // 2026/11/06：JSON 行为要求属于 system，字段结构由独立 Schema 开关控制。
                            if (settings.isIncludeSystem() && systems.add(guidance)) {
                                out.add("系统输入", guidance);
                            }
                            if (settings.isIncludeSchema() && !nativeSchema
                                    && declarations.add(schema)) {
                                out.add("结构说明", schema);
                            }
                        } else if (settings.isIncludeSystem() && message.getRole()
                                == org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.LLMMessageRole.SYSTEM) {
                            var text = content(message);
                            if (systems.add(text)) {
                                out.message("系统输入", message);
                            }
                        }
                    }
                });
            }
            if (settings.isIncludeTools()) {
                result.tools().forEach(tool -> {
                    out.add("工具调用", "名称：" + tool.call().getName() + "；编号：" + tool.call().getId() + "；状态：" + tool.status());
                    out.add("  参数", tool.call().getArguments());
                    out.add("  结果", tool.output());
                });
            }
        }
        if (settings.isIncludeReasoning()) {
            for (var attempt : result.attempts()) {
                attempt.response().ifPresent(response -> {
                    out.add("第 " + attempt.number() + " 次模型推理", "模型：" + response.getModel());
                    response.getChoices().stream().filter(choice -> choice.getMessage() != null)
                            .forEach(choice -> out.reasoning(choice.getMessage().getProviderMetadata()));
                });
            }
        }
        return out.body + (out.truncated ? "\n  [正文已截断，上限=" + out.maximum + " 字符]" : "");
    }

    /**
     * 只输出指定消息的正文，历史助手及工具回填不通过本入口遍历。
     * @param label 稳定分组名
     * @param message 选中的消息
     */
    private void message(String label, ILLMMessage message) {
        // 2026/11/06：各内容段独立脱敏，不能先拼接占位符破坏 JSON 的安全解析边界。
        int index = 0;
        for (var content : message.getContents()) {
            add(label + (index++ == 0 ? "" : "（续）"),
                    content.getContentPart() instanceof TextLLMContentPart text
                            ? text.getText() : "[非文本内容已隐藏]");
        }
    }

    /**
     * 将多段文本合为一个可读字段，图像及其他二进制仅显示占位。
     * @param message 当前消息
     * @return 不含运行对象的原始文本
     */
    private static String content(ILLMMessage message) {
        return message.getContents().stream().map(content -> content.getContentPart() instanceof TextLLMContentPart text
                ? text.getText() : "[非文本内容已隐藏]").collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * 2026/11/06：结构按内容单独去重，toolChoice 的续轮变化不能让整份工具 Schema 重印。
     * @param declaration 宿主序列化的协议声明
     * @param seen 本请求内已输出的固定结构
     */
    private void protocol(String declaration, java.util.Set<String> seen) {
        var root = JSON.readTree(declaration);
        var schema = root.path("responseSchema");
        if (!schema.isMissingNode() && seen.add(schema.toString())) {
            add("输出结构", schema.toString());
        }
        var tools = root.path("tools");
        if (!tools.isMissingNode() && !tools.isEmpty() && seen.add("tools:" + tools)) {
            add("协议声明", tools.toString());
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
     * 2026/11/06：先脱敏再按文字分组缩进，换行仅属于同一日志事件且不能伪装日志前缀。
     * @param label 固定字段名
     * @param value 实际字段值
     */
    private void add(String label, @org.jspecify.annotations.Nullable String value) {
        if (truncated || value == null || value.isEmpty()) {
            return;
        }
        // 2026/11/06：包含 JSON 编码字符串的工具结果、参数、输入和推理使用同一脱敏入口。
        var safe = redactor.redact(value);
        var chunk = "\n  【" + label + "】\n    " + safe.replace("\r", "").replace("\n", "\n    ");
        int remaining = maximum - body.length();
        if (chunk.length() > remaining) {
            body.append(chunk, 0, remaining);
            truncated = true;
        } else {
            body.append(chunk);
        }
    }
}
