package org.zexnocs.teanekoagent.integration.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 将统一模型端口的完整消息、工具声明和响应写入独立诊断，不把应用预览冒充服务端上下文。
 * <br>2026/11/08：每次尝试独立关联请求，保留完整文本并沿用凭据及媒体脱敏。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
final class InteractionDiagnosticLog {
    private static final Logger LOG = LoggerFactory.getLogger("teaneko.llm.diagnostic");
    private static final JsonMapper JSON = new JsonMapper();

    /** 禁止实例化无状态诊断入口。 */
    private InteractionDiagnosticLog() {
    }

    /**
     * 诊断记录不受预览开关截断；脱敏处理容量不足时保留明确隐藏标记。
     * @param request 当前交互关联编号
     * @param result 实际尝试及终态
     */
    static void write(UUID request, LLMInteractionResult result) {
        if (!LOG.isInfoEnabled()) {
            return;
        }
        for (var attempt : result.attempts()) {
            LOG.info("{}", record(request, result, attempt));
        }
    }

    /**
     * 只序列化已经冻结的协议数据，凭据选项和运行句柄不进入诊断。
     * @param request 原交互编号
     * @param result 原交互终态
     * @param attempt 当前实际尝试
     * @return 脱敏后的单行 JSON，缺失输入和响应明确为空
     */
    static String record(UUID request, LLMInteractionResult result, LLMInteractionResult.ModelAttempt attempt) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("at", Instant.now().toString());
        fields.put("requestId", request.toString());
        fields.put("attempt", attempt.number());
        fields.put("status", result.status().name());
        fields.put("boundary", "APPLICATION_MODEL_PORT");
        fields.put("serverTokenContextAvailable", false);
        fields.put("redactionApplied", true);
        fields.put("request", attempt.input().map(input -> Map.of(
                "messages", input.messages().stream().map(InteractionDiagnosticLog::message).toList(),
                "declaration", JSON.readTree(input.declaration()))).orElse(null));
        fields.put("response", attempt.response().map(response -> {
            var value = new LinkedHashMap<String, Object>();
            value.put("id", response.getId());
            value.put("model", response.getModel());
            value.put("usage", response.getUsage());
            value.put("choices", response.getChoices().stream().map(choice -> {
                var candidate = new LinkedHashMap<String, Object>();
                candidate.put("index", choice.getIndex());
                candidate.put("finishReason", choice.getFinishReason());
                candidate.put("message", message(choice.getMessage()));
                candidate.put("logprobs", choice.getLogprobs());
                return candidate;
            }).toList());
            return value;
        }).orElse(null));
        return new ObservationContentRedactor().redact(JSON.writeValueAsString(fields));
    }

    /**
     * 保留角色、完整文本及工具配对，非文本载荷由后续脱敏器隐藏。
     * @param source 已冻结的统一消息
     * @return 纯数据诊断，不持有原消息或业务服务
     */
    private static Map<String, Object> message(ILLMMessage source) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("role", source.getRole().name());
        fields.put("name", source.getName());
        fields.put("contents", source.getContents());
        fields.put("providerMetadata", source.getProviderMetadata());
        if (source instanceof ILLMAssistantMessage assistant) {
            fields.put("toolCalls", assistant.getToolCalls());
        }
        if (source instanceof ILLMToolMessage tool) {
            fields.put("toolCallId", tool.getToolCallId());
        }
        return fields;
    }
}
