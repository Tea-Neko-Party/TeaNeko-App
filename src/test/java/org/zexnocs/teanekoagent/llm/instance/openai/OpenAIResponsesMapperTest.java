package org.zexnocs.teanekoagent.llm.instance.openai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.LLMObjectFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.LLMStringFunctionParameter;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesMapper;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesResponseData;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 映射测试。
 * <br>验证消息历史、Function Tool、输出 item 和 token usage 的双向转换。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class OpenAIResponsesMapperTest {
    /**
     * 验证历史 assistant function call 和 tool result 会转换为正确的 Responses input item。
     */
    @Test
    void mapsConversationAndFunctionCallHistoryToInputItems() {
        var assistant = LLMAssistantMessage.builder()
                .name("assistant-a")
                .contents(text("checking"))
                .toolCalls(List.of(LLMToolCall.builder()
                        .id("call-1")
                        .name("search_memory")
                        .arguments("{\"query\":\"tea\"}")
                        .build()))
                .build();
        var messages = List.<ILLMMessage>of(
                LLMSystemMessage.builder()
                        .name("policy")
                        .contents(text("system"))
                        .build(),
                assistant,
                LLMToolMessage.builder()
                        .name("search_memory")
                        .toolCallId("call-1")
                        .contents(text("result"))
                        .build()
        );

        var request = OpenAIResponsesMapper.toRequest(
                new LLMPrompt(messages),
                OpenAIModelOptions.baseOptions(),
                "test-key"
        );

        Assertions.assertEquals(4, request.getInput().size());
        Assertions.assertEquals("message", request.getInput().getFirst().get("type"));
        Assertions.assertEquals("system", request.getInput().get(0).get("role"));
        Assertions.assertFalse(request.getInput().get(0).containsKey("name"));
        Assertions.assertEquals("function_call", request.getInput().get(2).get("type"));
        Assertions.assertEquals("call-1", request.getInput().get(2).get("call_id"));
        Assertions.assertEquals("function_call_output", request.getInput().get(3).get("type"));
        Assertions.assertEquals("result", request.getInput().get(3).get("output"));
    }

    /**
     * 验证 Function Tool 使用 Responses API 的扁平定义，并正确映射 JSON 输出和 reasoning 参数。
     */
    @Test
    void mapsToolsAndOpenAIOptionsToRequest() {
        var tool = LLMTool.builder()
                .name("search_memory")
                .description("Search memory")
                .strict(true)
                .parameters(new LLMObjectFunctionParameter(
                        "query",
                        Map.of("query", new LLMStringFunctionParameter("keyword")),
                        List.of("query")
                ))
                .build();
        var options = OpenAIModelOptions.baseOptions();
        options.setThinking(true);
        options.setReasoningEffort("high");
        options.setReasoningSummary("auto");
        options.setResponseFormat(LLMResponseFormat.JSON);
        options.setVerbosity("low");
        options.setTools(List.of(tool));
        options.setToolChoice("auto");
        options.setLogprobs(true);
        options.setTopLogprobs(3);
        options.setStore(false);

        var request = OpenAIResponsesMapper.toRequest(
                new LLMPrompt(List.of()),
                options,
                "test-key"
        );

        var mappedTool = request.getTools().getFirst();
        Assertions.assertEquals("function", mappedTool.get("type"));
        Assertions.assertEquals("search_memory", mappedTool.get("name"));
        Assertions.assertFalse(mappedTool.containsKey("function"));
        Assertions.assertEquals(true, mappedTool.get("strict"));
        Assertions.assertEquals("high", request.getReasoning().get("effort"));
        Assertions.assertEquals("auto", request.getReasoning().get("summary"));
        Assertions.assertEquals(Map.of("type", "json_object"), request.getText().get("format"));
        Assertions.assertEquals("low", request.getText().get("verbosity"));
        Assertions.assertEquals(3, request.getTopLogprobs());
        Assertions.assertEquals(List.of("reasoning.encrypted_content"), request.getInclude());
    }

    /**
     * 验证运行时认证字段不会进入 JSON，空可选字段也不会被序列化。
     */
    @Test
    void omitsRuntimeAndEmptyOptionalFieldsFromJson() {
        var options = OpenAIModelOptions.baseOptions();
        options.setOrganization("org-test");
        options.setProject("project-test");
        options.setMetadata(Map.of(
                "body.empty", "",
                "body.keep", "value"
        ));
        var request = OpenAIResponsesMapper.toRequest(
                new LLMPrompt(List.of()),
                options,
                "secret-key"
        );

        var json = new ObjectMapper().writeValueAsString(request);

        Assertions.assertFalse(json.contains("secret-key"));
        Assertions.assertFalse(json.contains("org-test"));
        Assertions.assertFalse(json.contains("project-test"));
        Assertions.assertFalse(json.contains("\"empty\""));
        Assertions.assertTrue(json.contains("\"keep\":\"value\""));
        Assertions.assertEquals("Bearer secret-key", request.headers().get("Authorization"));
        Assertions.assertEquals("org-test", request.headers().get("OpenAI-Organization"));
        Assertions.assertEquals("project-test", request.headers().get("OpenAI-Project"));
    }

    /**
     * 验证输出文本、拒绝内容、Function Call 和 usage 会映射为统一 LLM Result。
     */
    @Test
    void mapsResponseOutputAndUsageToFrameworkResult() {
        var response = new OpenAIResponsesResponseData();
        response.setId("resp-1");
        response.setObject("response");
        response.setCreatedAt(1_765_000_000L);
        response.setModel("gpt-5.5");
        response.setStatus("completed");

        var outputText = new OpenAIResponsesResponseData.OutputContent();
        outputText.setType("output_text");
        outputText.setText("answer");
        outputText.setLogprobs(List.of(Map.of("token", "answer")));
        var refusal = new OpenAIResponsesResponseData.OutputContent();
        refusal.setType("refusal");
        refusal.setRefusal("refused detail");
        var message = new OpenAIResponsesResponseData.OutputItem();
        message.setType("message");
        message.setRole("assistant");
        message.setContent(List.of(outputText, refusal));

        var reasoning = new OpenAIResponsesResponseData.OutputItem();
        reasoning.setId("rs-1");
        reasoning.setType("reasoning");
        reasoning.setSummary(List.of(Map.of("type", "summary_text", "text", "summary")));
        reasoning.setEncryptedContent("encrypted");

        var functionCall = new OpenAIResponsesResponseData.OutputItem();
        functionCall.setType("function_call");
        functionCall.setCallId("call-2");
        functionCall.setName("search_memory");
        functionCall.setArguments("{\"query\":\"tea\"}");
        response.setOutput(List.of(reasoning, message, functionCall));

        var inputDetails = new OpenAIResponsesResponseData.InputTokenDetails();
        inputDetails.setCachedTokens(40);
        var outputDetails = new OpenAIResponsesResponseData.OutputTokenDetails();
        outputDetails.setReasoningTokens(20);
        var usage = new OpenAIResponsesResponseData.Usage();
        usage.setInputTokens(100);
        usage.setInputTokensDetails(inputDetails);
        usage.setOutputTokens(60);
        usage.setOutputTokensDetails(outputDetails);
        usage.setTotalTokens(160);
        response.setUsage(usage);

        var result = OpenAIResponsesMapper.toResult(response);
        var choice = result.getFirstChoice().orElseThrow();
        var assistant = result.getFirstMessage().orElseThrow();

        Assertions.assertEquals("tool_calls", choice.getFinishReason());
        Assertions.assertEquals("answer\nrefused detail", text(assistant.getContents()));
        Assertions.assertEquals("call-2", assistant.getToolCalls().getFirst().getId());
        var providerItems = Assertions.assertInstanceOf(
                List.class,
                assistant.getProviderMetadata().get(OpenAIResponsesMapper.RESPONSE_INPUT_ITEMS_METADATA)
        );
        Assertions.assertEquals("reasoning", ((Map<?, ?>) providerItems.getFirst()).get("type"));
        Assertions.assertEquals(100, result.getUsage().getPromptTokens());
        Assertions.assertEquals(40, result.getUsage().getPromptCacheHitTokens());
        Assertions.assertEquals(60, result.getUsage().getPromptCacheMissTokens());
        Assertions.assertEquals(20, result.getUsage().getReasoningTokens());

        var continuedRequest = OpenAIResponsesMapper.toRequest(
                new LLMPrompt(List.of(assistant)),
                OpenAIModelOptions.baseOptions(),
                "test-key"
        );
        Assertions.assertEquals("reasoning", continuedRequest.getInput().getFirst().get("type"));
    }

    /**
     * 构造单个文本 Content 列表。
     *
     * @param text 文本内容
     * @return LLM Content 列表
     */
    private static List<ILLMContent> text(String text) {
        return LLMContentListBuilder.builder().addText(text).build();
    }

    /**
     * 提取首个文本 Content。
     *
     * @param contents LLM Content 列表
     * @return 文本内容或空字符串
     */
    private static String text(List<ILLMContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        var part = contents.getFirst().getContentPart();
        return part instanceof TextLLMContentPart textPart ? textPart.getText() : "";
    }
}
