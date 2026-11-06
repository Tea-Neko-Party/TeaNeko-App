package org.zexnocs.teanekoagent.integration.monitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMRequestSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMTokenUsage;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证汇总保留实际输入和供应商字段，同时隐藏凭据、媒体与未公开状态。
 * <br>2026/11/06：覆盖有界长输入、内容开关及多次尝试的用量归属。
 * <br>2026/11/06：覆盖四条内容入口的编码 JSON、完整授权头、畸形输入与处理上限。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.5-l
 * @version 4.8.5-l
 */
@Timeout(10)
class InteractionLogFormatterTest {
    /** 完整工具链、推理和汇总都可检查，授权头和签名地址不能泄漏。 */
    @Test
    void includesActualContextAndPublicReasoningWithoutCredentials() {
        var result = result("用户问题\nAuthorization: Bearer hidden-bearer\napi-key=hidden-key "
                + "{\"password\":\"hidden-password\"} https://example.invalid/image?rkey=hidden-signature"
                + "\nCookie: session=hidden-cookie; other=hidden-cookie-two\n正常内容");
        var text = InteractionLogFormatter.details(result, new AgentObservationFileConfig());
        for (var expected : List.of("用户问题", "协议声明", "lookup", "工具回填内容", "最终答案", "供应商提供的思考", "公开推理摘要")) {
            assertTrue(text.contains(expected), expected);
        }
        for (var hidden : List.of("hidden-bearer", "hidden-key", "hidden-password", "hidden-signature",
                "hidden-metadata", "hidden-encrypted", "hidden-image", "hidden-cookie", "hidden-cookie-two")) {
            assertFalse(text.contains(hidden), hidden);
        }
        assertFalse(text.contains("\n"), "换行应转义，不能将一条请求拆成多行");
        var metrics = InteractionLogFormatter.metrics(result);
        assertTrue(metrics.contains("已报告总token=23"));
        assertTrue(metrics.contains("估算token=11"));
        assertTrue(metrics.contains("用量未知次数=1"));
    }

    /** 关闭上下文与推理分别生效，不用正文开关隐藏最终状态统计。 */
    @Test
    void contentSwitchesDoNotChangeMetrics() {
        var result = result("用户问题");
        var settings = new AgentObservationFileConfig();
        settings.setIncludeContext(false);
        var text = InteractionLogFormatter.details(result, settings);
        assertFalse(text.contains("用户问题"));
        assertFalse(text.contains("最终答案"));
        assertTrue(text.contains("供应商提供的思考"));
        settings.setIncludeReasoning(false);
        assertEquals("", InteractionLogFormatter.details(result, settings));
        assertTrue(InteractionLogFormatter.metrics(result).contains("模型调用=3"));
    }

    /** 未启用预算时仍采用实际供应商用量，不把缺少 total 字段误报为零。 */
    @Test
    void countsSuppliedUsageEvenWithoutBudgetAccounting() {
        var response = LLMResult.builder().usage(LLMUsage.builder().promptTokens(20).completionTokens(9).build()).build();
        var result = new LLMInteractionResult(LLMInteractionResult.Status.EMPTY_OUTPUT, List.of(), null,
                List.of(new LLMInteractionResult.ModelAttempt(1, response)), List.of(), Map.of());
        assertTrue(InteractionLogFormatter.metrics(result).contains("已报告总token=29"));
        assertTrue(InteractionLogFormatter.metrics(result).contains("用量未知次数=0"));
    }

    /** 长密钥和正文采用有界输出；不能发生正则递归溢出或将截断冒充完整内容。 */
    @Test
    void longEscapedSecretIsRedactedBeforeTruncation() {
        var settings = new AgentObservationFileConfig();
        settings.setMaxContentCharacters(1024);
        var result = result("{\"api-key\":\"" + "s\\\"".repeat(100_000) + "hidden-end\"} " + "长正文".repeat(100_000));
        var text = InteractionLogFormatter.details(result, settings);
        assertTrue(text.contains("正文已截断"));
        assertFalse(text.contains("hidden-end"));
        assertTrue(text.length() < 1100);
        assertTrue(InteractionLogFormatter.metrics(result).contains("已报告总token=23"));
    }

    /**
     * 2026/11/06：审查的原始漏脱敏载荷经过每个真实内容入口，保留普通上下文及安全兄弟字段。
     * @param channel 输入、工具参数、工具结果或供应商推理
     */
    @ParameterizedTest
    @ValueSource(strings = {"input", "arguments", "output", "reasoning"})
    void protectsEncodedCredentialsAcrossContentChannels(String channel) {
        var json = new JsonMapper();
        var nested = "{\"body\":\"{\\\"api_key\\\":\\\"review-hidden-nested\\\"}\"}";
        var samples = List.of(nested,
                "{\"header\":\"Cookie: first=review-hidden-first; session=review-hidden-session\"}",
                "{\"\\u0061pi_key\":{\"items\":[\"review-hidden-object\"]},\"token\":[\"review-hidden-array\"]}",
                json.writeValueAsString(Map.of("body", json.writeValueAsString(Map.of("body", nested)))),
                "前文 Authorization: Bearer review-hidden-auth; extra=review-hidden-extra\n普通后文",
                "前文 Set-Cookie: first=review-hidden-set; session=review-hidden-second; Secure\n普通后文",
                "前文 Cookie: first=review-hidden-first;\r\n\tsession=review-hidden-continuation\n普通后文",
                "{\"body\":\"{\\\"api_key\\\":\\\"review-hidden-incomplete\"}",
                "{\"password\":\"review-hidden-open\nreview-hidden-tail",
                "正文 {\"api_key\":{\n\"value\":\"review-hidden-multiline\"}}",
                "{\"body\":\"{\\\"access_token\\\":\\\"review-hidden-nested\\\",\\\"note\\\":\\\"安全子字段\\\"}\"}");
        for (var sample : samples) {
            // 将安全字段放在有效 JSON 同级，不靠截断或改变内层编码绕过原始触发条件。
            var payload = json.writeValueAsString(Map.of("payload", sample, "note", "安全兄弟字段"));
            var result = result(channel.equals("input") ? payload : "用户问题",
                    channel.equals("arguments") ? payload : "{\"query\":\"茶\"}",
                    channel.equals("output") ? payload : "工具回填内容",
                    channel.equals("reasoning") ? payload : "供应商提供的思考");
            var text = InteractionLogFormatter.details(result, new AgentObservationFileConfig());
            assertFalse(text.contains("review-hidden-"), channel + ": " + sample);
            assertTrue(text.contains("安全兄弟字段"), channel);
            assertTrue(text.contains("最终答案"));
            assertFalse(text.contains("正文已截断"));
            assertFalse(text.contains("\n"));
            if (sample.contains("安全子字段")) {
                assertTrue(text.contains("安全子字段"));
            }
        }
        // 原始两条载荷不增加编码层，直接从同一入口复现审查场景。
        for (var payload : samples.subList(0, 2)) {
            var text = InteractionLogFormatter.details(result(channel.equals("input") ? payload : "用户问题",
                    channel.equals("arguments") ? payload : "{}", channel.equals("output") ? payload : "工具回填内容",
                    channel.equals("reasoning") ? payload : "供应商提供的思考"), new AgentObservationFileConfig());
            assertFalse(text.contains("review-hidden-"));
            assertTrue(text.contains("最终答案"));
        }
    }

    /** 2026/11/06：编码层数、结构深度和超长输入超限时明确隐藏，不能回退原文或只截掉尾部。 */
    @Test
    void hidesUnprocessableContentBeforeOutputTruncation() {
        var encoded = "{\"api_key\":\"review-hidden-deep\"}";
        var json = new JsonMapper();
        for (int index = 0; index < 6; index++) {
            encoded = json.writeValueAsString(Map.of("body", encoded));
        }
        for (var payload : List.of(encoded,
                "[".repeat(40) + "\"review-hidden-depth\"" + "]".repeat(40),
                "review-hidden-oversize" + "x".repeat(2_000_001),
                "[" + "0,".repeat(45_000) + "\"review-hidden-nodes\"]")) {
            var text = InteractionLogFormatter.details(result(payload), new AgentObservationFileConfig());
            assertFalse(text.contains("review-hidden-"));
            assertTrue(text.contains("超出脱敏处理上限"));
            assertTrue(InteractionLogFormatter.metrics(result(payload)).contains("已报告总token=23"));
        }
    }

    /** 2026/11/06：跨字段的处理预算耗尽后继续隐藏，不能为后一个字段重置额度。 */
    @Test
    void sharesProcessingLimitAcrossAllFields() {
        var redactor = new ObservationContentRedactor();
        var json = new JsonMapper();
        var largeSecret = json.writeValueAsString(Map.of("api_key", "review-hidden-" + "x".repeat(1_800_000)));
        for (int index = 0; index < 4; index++) {
            assertFalse(redactor.redact(largeSecret).contains("review-hidden-"));
        }
        assertTrue(redactor.redact(largeSecret).contains("超出脱敏处理上限"));
        assertTrue(redactor.redact("review-hidden-after-limit").contains("超出脱敏处理上限"));
    }

    /**
     * 构造已完成的实际协议结果，不调用真实模型或数据库。
     * @param input 用户文本
     * @return 含三次尝试和一次工具回填的汇总输入
     */
    private static LLMInteractionResult result(String input) {
        return result(input, "{\"query\":\"茶\"}", "工具回填内容", "供应商提供的思考");
    }

    /**
     * 2026/11/06：四种真实字段共用原交互夹具，实际工具声明与回填仍成对出现。
     * @param input 输入文本
     * @param arguments 工具参数
     * @param output 工具回填
     * @param reasoning 供应商推理字段
     * @return 可交给生产格式器的完整交互结果
     */
    private static LLMInteractionResult result(String input, String arguments, String output, String reasoning) {
        var user = LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(input)
                .addImage("https://example.invalid/hidden-image", LLMImageDetail.AUTO).build()).build();
        var call = LLMToolCall.builder().id("call-1").name("lookup").arguments(arguments).build();
        var toolRequest = LLMAssistantMessage.builder().toolCalls(List.of(call)).build();
        var tool = new ToolInvocationResult(call, ToolInvocationResult.Status.SUCCESS, output, false);
        var answer = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("最终答案").build())
                .providerMetadata(Map.of("test.reasoningContent", reasoning,
                        "test.responseInputItems", List.of(Map.of("type", "reasoning", "encrypted_content", "hidden-encrypted",
                                "summary", List.of(Map.of("text", "公开推理摘要")))), "test.other", "hidden-metadata")).build();
        var response = LLMResult.builder().model("test-model").choices(List.of(LLMChoice.builder()
                .message(answer).finishReason("stop").build())).build();
        var snapshot = new LLMRequestSnapshot(List.of(user, toolRequest, tool.toMessage()), "{\"tools\":[\"lookup\"]}");
        var attempts = List.of(
                new LLMInteractionResult.ModelAttempt(1, Optional.of(response),
                        Optional.of(new LLMTokenUsage(23, LLMTokenUsage.Source.REPORTED, 0, "", false)), Optional.of(snapshot)),
                new LLMInteractionResult.ModelAttempt(2, Optional.empty(),
                        Optional.of(new LLMTokenUsage(11, LLMTokenUsage.Source.ESTIMATED, 11, "test", false))),
                new LLMInteractionResult.ModelAttempt(3, null));
        return new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(user, toolRequest, tool.toMessage(), answer),
                answer, attempts, List.of(tool), Map.of());
    }
}
