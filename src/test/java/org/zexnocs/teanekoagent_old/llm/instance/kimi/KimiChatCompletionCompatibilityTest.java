package org.zexnocs.teanekoagent_old.llm.instance.kimi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent_old.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent_old.llm.framework.tool.parameter.LLMObjectFunctionParameter;
import org.zexnocs.teanekoagent_old.llm.framework.tool.parameter.LLMStringFunctionParameter;
import org.zexnocs.teanekoagent_old.llm.instance.openai.completions.OpenAIChatCompletionMapper;
import org.zexnocs.teanekoagent_old.llm.instance.openai.completions.OpenAIChatCompletionResponseData;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Kimi 对 Chat Completions 通用层的兼容测试。
 * <br>验证通用消息与 Function Tool 映射，以及 Kimi thinking 和 token usage 扩展。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class KimiChatCompletionCompatibilityTest {
    /**
     * 验证 assistant reasoning_content 和工具结果能够按 Kimi 多轮调用要求回传。
     */
    @Test
    void mapsReasoningAndToolHistoryToChatMessages() {
        var assistant = LLMAssistantMessage.builder()
                .contents(text("checking"))
                .toolCalls(List.of(LLMToolCall.builder()
                        .id("call-1")
                        .name("search_memory")
                        .arguments("{\"query\":\"tea\"}")
                        .build()))
                .providerMetadata(Map.of(
                        OpenAIChatCompletionMapper.REASONING_CONTENT_METADATA,
                        "private reasoning"
                ))
                .build();
        var messages = List.<ILLMMessage>of(
                LLMUserMessage.builder()
                        .name("alice")
                        .contents(text("query"))
                        .build(),
                assistant,
                LLMToolMessage.builder()
                        .name("search_memory")
                        .toolCallId("call-1")
                        .contents(text("result"))
                        .build()
        );

        var request = OpenAIChatCompletionMapper.toRequest(
                new LLMPrompt(messages),
                KimiModelOptions.baseOptions(),
                "test-key"
        );

        Assertions.assertEquals("alice", request.getMessages().get(0).get("name"));
        Assertions.assertEquals("private reasoning", request.getMessages().get(1).get("reasoning_content"));
        Assertions.assertEquals("call-1", request.getMessages().get(2).get("tool_call_id"));
        Assertions.assertEquals("result", request.getMessages().get(2).get("content"));
    }

    /**
     * 验证 K2.6 thinking、Function Tool 和 Kimi 专用缓存参数能够写入请求。
     */
    @Test
    void mapsKimiOptionsAndFunctionTools() {
        var options = KimiModelOptions.baseOptions();
        options.setThinking(true);
        options.setThinkingKeep(true);
        options.setPromptCacheKey("cache-key");
        options.setSafetyIdentifier("user-hash");
        options.setTools(List.of(LLMTool.builder()
                .name("search_memory")
                .description("Search memory")
                .strict(false)
                .parameters(new LLMObjectFunctionParameter(
                        "query",
                        Map.of("query", new LLMStringFunctionParameter("keyword")),
                        List.of("query")
                ))
                .build()));

        var request = OpenAIChatCompletionMapper.toRequest(
                new LLMPrompt(List.of()),
                options,
                "test-key"
        );

        var thinking = Assertions.assertInstanceOf(Map.class, request.getExtraBody().get("thinking"));
        Assertions.assertEquals("enabled", thinking.get("type"));
        Assertions.assertEquals(true, thinking.get("keep"));
        Assertions.assertEquals("cache-key", request.getExtraBody().get("prompt_cache_key"));
        Assertions.assertEquals("user-hash", request.getExtraBody().get("safety_identifier"));
        var function = Assertions.assertInstanceOf(
                Map.class,
                request.getTools().getFirst().get("function")
        );
        Assertions.assertEquals("search_memory", function.get("name"));
        Assertions.assertEquals(false, function.get("strict"));
    }

    /**
     * 验证 K2.7 Code 不发送不受支持的 thinking 字段，认证信息也不会进入 JSON。
     */
    @Test
    void omitsThinkingForK27CodeAndHidesApiKey() {
        var options = KimiModelOptions.baseOptions();
        options.setModel(KimiModelOptions.KIMI_K2_7_CODE);
        options.setThinking(true);
        var request = OpenAIChatCompletionMapper.toRequest(
                new LLMPrompt(List.of()),
                options,
                "secret-key"
        );

        var json = new ObjectMapper().writeValueAsString(request);

        Assertions.assertFalse(request.getExtraBody().containsKey("thinking"));
        Assertions.assertFalse(json.contains("secret-key"));
        Assertions.assertEquals("Bearer secret-key", request.headers().get("Authorization"));
    }

    /**
     * 验证响应正文、隐藏 reasoning_content、工具调用和缓存 token 会映射为统一结果。
     */
    @Test
    void mapsResponseReasoningToolCallsAndUsage() {
        var response = new OpenAIChatCompletionResponseData();
        response.setId("chat-1");
        response.setObject("chat.completion");
        response.setCreated(1_765_000_000L);
        response.setModel("kimi-k2.6");

        var function = new OpenAIChatCompletionResponseData.FunctionCall();
        function.setName("search_memory");
        function.setArguments("{\"query\":\"tea\"}");
        var toolCall = new OpenAIChatCompletionResponseData.ToolCall();
        toolCall.setId("call-2");
        toolCall.setType("function");
        toolCall.setFunction(function);
        var message = new OpenAIChatCompletionResponseData.Message();
        message.setContent("answer");
        message.setReasoningContent("private reasoning");
        message.setToolCalls(List.of(toolCall));
        var choice = new OpenAIChatCompletionResponseData.Choice();
        choice.setIndex(0);
        choice.setFinishReason("tool_calls");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        var promptDetails = new OpenAIChatCompletionResponseData.PromptTokenDetails();
        promptDetails.setCachedTokens(30);
        var completionDetails = new OpenAIChatCompletionResponseData.CompletionTokenDetails();
        completionDetails.setReasoningTokens(10);
        var usage = new OpenAIChatCompletionResponseData.Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setTotalTokens(150);
        usage.setPromptTokenDetails(promptDetails);
        usage.setCompletionTokenDetails(completionDetails);
        response.setUsage(usage);

        var result = OpenAIChatCompletionMapper.toResult(response);
        var assistant = result.getFirstMessage().orElseThrow();

        Assertions.assertEquals("answer", text(assistant.getContents()));
        Assertions.assertEquals("private reasoning", assistant.getProviderMetadata()
                .get(OpenAIChatCompletionMapper.REASONING_CONTENT_METADATA));
        Assertions.assertEquals("call-2", assistant.getToolCalls().getFirst().getId());
        Assertions.assertEquals(30, result.getUsage().getPromptCacheHitTokens());
        Assertions.assertEquals(70, result.getUsage().getPromptCacheMissTokens());
        Assertions.assertEquals(10, result.getUsage().getReasoningTokens());
    }

    /**
     * 构造单个文本 Content 列表。
     *
     * @param value 文本内容
     * @return LLM Content 列表
     */
    private static List<ILLMContent> text(String value) {
        return LLMContentListBuilder.builder().addText(value).build();
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
