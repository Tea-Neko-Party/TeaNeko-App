package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.JsonLLMTokenEstimator;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonSchema;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原生协议离线回归，验证合同字段、图片、签名工具往返及明确终态。
 * <br>2026/11/10：复用统一快照和估算器，确保原生状态无法替换正文或绕过容量预算。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
class AntigravityProtocolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LLMProviderAccess access = new LLMProviderAccess("synthetic-key", "https://proxy.invalid", "/v1beta/models");

    /** @return 带明确输出额度的合成路由选项。 */
    private LLMModelOptions options() {
        return LLMModelOptions.builder().provider("gemini").model("gemini-3.8-flash-medium")
                .maxTokens(163840).build();
    }

    /**
     * @param text 合成正文
     * @return 统一用户消息
     */
    private LLMUserMessage user(String text) {
        return LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build();
    }

    /** Schema、系统提示和完整输出额度进入原生字段，认证仅进入头。 */
    @Test
    void carriesNestedSchemaAndCredentialsInTheirOwnBoundaries() {
        var options = options();
        options.setResponseFormat(LLMResponseFormat.JSON);
        var schema = LLMJsonSchema.fromType(StyleObservations.Analysis.class);
        options.setResponseSchema(schema);
        var prompt = new LLMPrompt(List.of(LLMSystemMessage.builder()
                .contents(new LLMContentListBuilder().addText("合成系统约束").build()).build(), user("合成资料")), options);
        var request = AntigravityRequestMapper.toRequest(prompt, options, access, mapper);
        var body = mapper.valueToTree(request);
        assertEquals("application/json", body.at("/generationConfig/responseMimeType").asString());
        assertEquals(mapper.valueToTree(schema.schema()), body.at("/generationConfig/responseJsonSchema"));
        assertTrue(body.at("/generationConfig/responseJsonSchema/properties/styles/items/required")
                .toString().contains("context"));
        assertEquals(163840, body.at("/generationConfig/maxOutputTokens").asInt());
        assertEquals("合成系统约束", body.at("/systemInstruction/parts/0/text").asString());
        assertEquals(1, body.path("contents").size());
        // 2026/11/10：默认流式传输继续完整携带 Schema，显式关闭时保留原路径。
        assertEquals("/v1beta/models/gemini-3.8-flash-medium:streamGenerateContent?alt=sse", request.getPathOverride());
        options.setStream(false);
        assertEquals("/v1beta/models/gemini-3.8-flash-medium:generateContent",
                AntigravityRequestMapper.toRequest(prompt, options, access, mapper).getPathOverride());
        assertEquals(Map.of("x-goog-api-key", "synthetic-key"), request.headers());
        assertFalse(mapper.writeValueAsString(request).contains("synthetic-key"));
        assertFalse(request.toString().contains("synthetic-key"));
        assertFalse(body.has("response_format"));
        assertFalse(body.has("model"));
    }

    /** 内联图片使用原生数据部件，远端图片仅转交引用，不自行下载。 */
    @Test
    void mapsNativeImagesAndToolDeclarations() {
        var options = options();
        options.setTools(List.of(LLMTool.builder().name("synthetic_tool").description("合成工具").build()));
        options.setToolChoice("required");
        var image = LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("识别合成图片")
                .addImage("data:image/png;base64,AQID").addImage("https://example.invalid/test.png").build()).build();
        var body = mapper.valueToTree(AntigravityRequestMapper.toRequest(new LLMPrompt(image, options), options, access, mapper));
        assertEquals("image/png", body.at("/contents/0/parts/1/inlineData/mimeType").asString());
        assertEquals("AQID", body.at("/contents/0/parts/1/inlineData/data").asString());
        assertEquals("https://example.invalid/test.png", body.at("/contents/0/parts/2/fileData/fileUri").asString());
        assertEquals("synthetic_tool", body.at("/tools/0/functionDeclarations/0/name").asString());
        assertTrue(body.at("/tools/0/functionDeclarations/0/parametersJsonSchema").isObject());
        assertEquals("ANY", body.at("/toolConfig/functionCallingConfig/mode").asString());
    }

    /** 并行工具的原始签名和部件顺序往返不变，未提供的上游 ID 不由本地补号冒充。 */
    @Test
    void replaysSignaturesAndMatchesParallelToolResults() {
        var parts = mapper.readTree("""
                [{"text":"合成隐藏思考","thought":true,"thoughtSignature":"opaque-signature"},
                 {"functionCall":{"name":"one","args":{"value":1}},"thoughtSignature":"first-signature"},
                 {"functionCall":{"name":"two","args":{},"id":"upstream-two"}}]
                """);
        var assistant = AntigravityMessages.decode(parts, mapper);
        assertTrue(assistant.getContents().isEmpty());
        var messages = new ArrayList<ILLMMessage>();
        messages.add(user("合成工具请求"));
        messages.add(assistant);
        for (var call : assistant.getToolCalls()) {
            messages.add(LLMToolMessage.builder().toolCallId(call.getId())
                    .contents(new LLMContentListBuilder().addText("合成执行结果").build()).build());
        }
        var snapshot = LLMProtocolSnapshots.messages(messages);
        var encoded = mapper.valueToTree(AntigravityMessages.encode(snapshot, mapper).contents());
        assertEquals(parts, encoded.at("/1/parts"));
        assertEquals(2, encoded.at("/2/parts").size());
        assertEquals("one", encoded.at("/2/parts/0/functionResponse/name").asString());
        assertFalse(encoded.at("/2/parts/0/functionResponse").has("id"));
        assertEquals("upstream-two", encoded.at("/2/parts/1/functionResponse/id").asString());
        var estimator = new JsonLLMTokenEstimator(mapper);
        assertTrue(estimator.estimate(new LLMPrompt(snapshot)).inputTokens()
                > estimator.estimate(new LLMPrompt(LLMProtocolSnapshots.independentMessages(snapshot))).inputTokens());
        assistant.setContents(new LLMContentListBuilder().addText("被修改的正文").build());
        assertThrows(IllegalArgumentException.class, () -> AntigravityMessages.encode(messages, mapper));
    }

    /** 错误回放或孤立工具结果在发送前拒绝，不能被透明状态覆盖。 */
    @Test
    void rejectsChangedArgumentsOrIncompleteToolPairs() {
        var assistant = AntigravityMessages.decode(mapper.readTree("""
                [{"functionCall":{"name":"one","args":{"x":1}}}]
                """), mapper);
        assertThrows(IllegalArgumentException.class, () -> AntigravityMessages.encode(List.of(user("测试"), assistant), mapper));
        var result = LLMToolMessage.builder().toolCallId(assistant.getToolCalls().getFirst().getId())
                .contents(new LLMContentListBuilder().addText("结果").build()).build();
        assertThrows(IllegalArgumentException.class, () -> AntigravityMessages.encode(List.of(result), mapper));
        var call = assertInstanceOf(org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall.class,
                assistant.getToolCalls().getFirst());
        call.setArguments("{\"x\":2}");
        assertThrows(IllegalArgumentException.class, () -> AntigravityMessages.encode(List.of(user("测试"), assistant, result), mapper));
    }

    /** 原生 args 可省略，但显式非对象参数不能被替换成空参数掩盖协议错误。 */
    @Test
    void rejectsInvalidArgumentsInsteadOfTreatingThemAsAbsent() {
        for (String args : List.of("null", "[]", "\"value\"")) {
            var parts = mapper.readTree("[{\"functionCall\":{\"name\":\"work\",\"args\":" + args + "}}]");
            assertThrows(IllegalArgumentException.class, () -> AntigravityMessages.decode(parts, mapper));
        }
    }

    /**
     * @param nativeReason 原生终态
     * @param expected 统一终态
     */
    @ParameterizedTest
    @CsvSource({"STOP,stop", "MAX_TOKENS,length", "SAFETY,content_filter", "RECITATION,content_filter",
            "MALFORMED_FUNCTION_CALL,error", "UNEXPECTED_TOOL_CALL,error", "UNKNOWN_REASON,error"})
    void preservesTerminalReasons(String nativeReason, String expected) {
        var response = mapper.readValue("""
                {"modelVersion":"actual-model","candidates":[{"index":0,"finishReason":"%s",
                "content":{"role":"model","parts":[{"text":"合成输出"}]}}],
                "usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":7,"thoughtsTokenCount":3,
                "totalTokenCount":21,"cachedContentTokenCount":4}}
                """.formatted(nativeReason), AntigravityResponseData.class);
        var result = AntigravityResponseMapper.toResult(response, mapper);
        assertEquals(expected, result.getChoices().getFirst().getFinishReason());
        assertEquals("actual-model", result.getModel());
        assertEquals(21, result.getUsage().getTotalTokens());
        assertEquals(10, result.getUsage().getCompletionTokens());
        assertEquals(3, result.getUsage().getReasoningTokens());
        assertEquals(7, result.getUsage().getPromptCacheMissTokens());
    }

    /** 输入阻止不要求存在候选；没有真实用量时不制造零用量。 */
    @Test
    void distinguishesBlockedInputAndMissingUsage() {
        var response = mapper.readValue("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", AntigravityResponseData.class);
        var result = AntigravityResponseMapper.toResult(response, mapper);
        assertEquals("content_filter", result.getChoices().getFirst().getFinishReason());
        assertNull(result.getUsage());
        assertThrows(IllegalArgumentException.class, () -> AntigravityMessages.decode(
                mapper.readTree("[{\"inlineData\":{\"data\":\"AQID\"}}]"), mapper));
    }

    /** 原生参数不允许路径逃逸、额外正文覆盖、静默忽略指定工具或冲突思考设置。 */
    @Test
    void rejectsUnsafePathAndControlledFieldOverrides() {
        for (String model : List.of("../other", "x:delete", "x?key=other", "x/y", "x%2Fy")) {
            assertThrows(IllegalArgumentException.class, () -> AntigravityRequestMapper.modelPath("/v1beta/models", model));
        }
        assertEquals("/prefix/v1beta/models/gemini:generateContent",
                AntigravityRequestMapper.modelPath("/prefix/v1beta/models/", "models/gemini"));
        for (var metadata : List.of(Map.<String, Object>of("body.generationConfig", Map.of()),
                Map.<String, Object>of("antigravity.thinkingLevel", "high", "antigravity.thinkingBudget", 10))) {
            var options = options();
            options.setMetadata(metadata);
            assertThrows(IllegalArgumentException.class, () -> AntigravityRequestMapper.toRequest(
                    new LLMPrompt(user("测试"), options), options, access, mapper));
        }
    }
}
