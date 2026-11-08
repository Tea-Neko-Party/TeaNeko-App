package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonCodec;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonSchema;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonSchemas;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplyPlan;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 表达插件拥有的旧 JSON 回复信封解析，普通图文交给通用交付投影。
 * <br>2026/11/07：保持原严格字段、容量及形式检查，系统原生组件不再解释业务协议。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class ExpressionReplyPlanParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LLMJsonCodec CODEC = new LLMJsonCodec(JSON);
    private static final LLMJsonSchemas.Definition LEGACY = new LLMJsonSchemas.Definition(
            LLMJsonSchema.fromType(LegacyReplyOutput.class), 64000);

    /** 禁止创建无状态解析工具实例。 */
    private ExpressionReplyPlanParser() {}

    /**
     * @param answer 已确认的正式答案
     * @return 完整图文计划或明确无发言
     */
    public static Optional<AgentReplyPlan> fromAnswer(ILLMAssistantMessage answer) {
        return fromAnswer(answer, null);
    }

    /**
     * @param answer 已确认的正式答案
     * @param request 可用的真实请求号
     * @return 完整校验后冻结的图文计划
     * @throws IllegalArgumentException 当信封含非法字段、额外媒体或超限内容时
     */
    public static Optional<AgentReplyPlan> fromAnswer(ILLMAssistantMessage answer, @Nullable UUID request) {
        String text = answer.getContents().stream().map(ILLMContent::getContentPart)
                .filter(TextLLMContentPart.class::isInstance)
                .map(value -> ((TextLLMContentPart) value).getText()).collect(Collectors.joining("\n"));
        if (!text.stripLeading().startsWith("{") || !text.contains("\"teaneko_reply\"")) {
            return AgentReplyPlan.fromAnswer(answer, request);
        }
        if (answer.getContents().stream().anyMatch(value -> !(value.getContentPart() instanceof TextLLMContentPart))) {
            throw new IllegalArgumentException("回复形式信封不能与额外图片内容混用。");
        }
        var output = CODEC.read(LegacyReplyOutput.class, LEGACY, text, request);
        var parts = new ArrayList<AgentReplyPlan.IPart>();
        for (var item : output.messages()) {
            var mode = switch (item.mode()) {
                case "reply" -> AgentReplyPlan.Mode.REPLY;
                case "at" -> AgentReplyPlan.Mode.AT;
                case "plain" -> AgentReplyPlan.Mode.PLAIN;
                default -> throw new IllegalArgumentException("Agent 回复形式不被支持。");
            };
            parts.add(new AgentReplyPlan.Text(item.text(), mode));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(new AgentReplyPlan(parts));
    }
}
