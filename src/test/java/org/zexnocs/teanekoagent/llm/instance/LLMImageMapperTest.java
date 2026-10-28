package org.zexnocs.teanekoagent.llm.instance;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.*;
import org.zexnocs.teanekoagent.llm.framework.message.content.DefaultContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.kimi.KimiModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesMapper;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对照各供应商真实请求结构验证图文顺序与透明工具推理状态。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMImageMapperTest {
    private static final String URL = "https://example.test/image.png";
    private static final String INLINE = "data:image/png;base64,AQID";

    /**
     * 验证DeepSeek 混合图文映射为保持原顺序的真实协议块。
     */
    @Test
    void deepSeekMapsMixedImagesToRealChatContentBlocksInOrder() {
        var prompt = prompt(LLMImageDetail.LOW);
        var request = DeepSeekChatCompletionMapper.toRequest(prompt, LLMModelOptions.builder().model("deepseek-flash").build(),
                "https://api.deepseek.com", "/chat/completions", "fake-key");
        var content = blocks(request.getMessages().getFirst().get("content"));
        assertEquals(List.of("text", "image_url", "text", "image_url"), content.stream().map(c -> c.get("type")).toList());
        assertEquals(Map.of("url", URL, "detail", "low"), content.get(1).get("image_url"));
        assertEquals(Map.of("url", INLINE), content.get(3).get("image_url"));
        var json = new ObjectMapper().writeValueAsString(request);
        assertTrue(json.contains("\"content\":["));
        assertFalse(json.contains("fake-key"));
    }

    /**
     * 验证Responses 使用对应的输入图片及文本块。
     */
    @Test
    void openAIResponsesUsesInputImageAndInputTextBlocks() {
        var request = OpenAIResponsesMapper.toRequest(prompt(LLMImageDetail.ORIGINAL), OpenAIModelOptions.baseOptions(), "fake-key");
        var content = blocks(request.getInput().getFirst().get("content"));
        assertEquals(List.of("input_text", "input_image", "input_text", "input_image"), content.stream().map(c -> c.get("type")).toList());
        assertEquals(URL, content.get(1).get("image_url"));
        assertEquals("original", content.get(1).get("detail"));
        assertEquals(INLINE, content.get(3).get("image_url"));
        assertEquals("auto", content.get(3).get("detail"));
        assertTrue(new ObjectMapper().writeValueAsString(request).contains("\"type\":\"input_image\""));
    }

    /**
     * 验证Kimi 使用图文数组并省略未声明的细节参数。
     */
    @Test
    void kimiUsesChatArraysAndOmitsUnspecifiedDetail() {
        var request = OpenAIChatCompletionMapper.toRequest(prompt(LLMImageDetail.AUTO), KimiModelOptions.baseOptions(), "fake-key");
        var content = blocks(request.getMessages().getFirst().get("content"));
        assertEquals(Map.of("url", URL), content.get(1).get("image_url"));
        assertEquals(Map.of("url", INLINE), content.get(3).get("image_url"));
        assertTrue(new ObjectMapper().writeValueAsString(request).contains("\"content\":["));
    }

    /**
     * 验证图像轮次后仍保留工具 ID 和透明推理状态。
     */
    @Test
    void chatCompletionsAndResponsesKeepToolIdsAndOpaqueReasoningAfterImageTurn() {
        var call = LLMToolCall.builder().id("call-7").name("lookup").arguments("{}").build();
        var reasoning = Map.<String, Object>of("type", "reasoning", "id", "reason-1", "encrypted_content", "opaque");
        var assistant = LLMAssistantMessage.builder().contents(List.of()).toolCalls(List.of(call))
                .providerMetadata(Map.of(OpenAIResponsesMapper.RESPONSE_INPUT_ITEMS_METADATA, List.of(reasoning),
                        OpenAIChatCompletionMapper.REASONING_CONTENT_METADATA, "vendor-reasoning")).build();
        var output = LLMToolMessage.builder().toolCallId("call-7").contents(LLMContentListBuilder.builder().addText("found").build()).build();
        var prompt = new LLMPrompt(List.of(prompt(LLMImageDetail.AUTO).getMessages().getFirst(), assistant, output));
        var chat = OpenAIChatCompletionMapper.toRequest(prompt, OpenAIChatCompletionModelOptions.baseOptions(), "fake-key");
        assertEquals("vendor-reasoning", chat.getMessages().get(1).get("reasoning_content"));
        assertEquals("call-7", chat.getMessages().get(2).get("tool_call_id"));
        var response = OpenAIResponsesMapper.toRequest(prompt, OpenAIModelOptions.baseOptions(), "fake-key");
        assertEquals(reasoning, response.getInput().get(1));
        assertEquals("call-7", response.getInput().get(2).get("call_id"));
        assertEquals("call-7", response.getInput().get(3).get("call_id"));
        assertEquals("found", response.getInput().get(3).get("output"));
    }

    /**
     * 验证所有 mapper 拒绝未知内容或非用户图片，不以字符串降级绕过。
     */
    @Test
    void everyMapperRejectsUnknownPartsAndNonUserImagesInsteadOfStringifying() {
        var unknown = LLMContent.builder().type("audio").contentPart(DefaultContentPart.builder().dataMap(Map.of("payload", "not-text")).build()).build();
        var mismatch = LLMContent.builder().type("image").contentPart(new TextLLMContentPart("not-image")).build();
        for (var contents : List.of(List.<ILLMContent>of(unknown), List.<ILLMContent>of(mismatch))) {
            assertAllMappersReject(new LLMPrompt(LLMUserMessage.builder().contents(contents).build()));
        }
        var images = LLMContentListBuilder.builder().addImage(URL).build();
        for (var message : List.<ILLMMessage>of(LLMSystemMessage.builder().contents(images).build(),
                LLMAssistantMessage.builder().contents(images).build(), LLMToolMessage.builder().toolCallId("call-7").contents(images).build())) {
            assertAllMappersReject(new LLMPrompt(message));
        }
    }

    /**
     * 验证透明供应商状态不能夹带绕过统一验证的原生图像消息。
     */
    @Test
    void opaqueProviderStateCannotSmuggleNativeImageMessages() {
        var state = Map.<String, Object>of(OpenAIResponsesMapper.RESPONSE_INPUT_ITEMS_METADATA,
                List.of(Map.of("type", "message", "role", "user", "content", List.of(Map.of("type", "input_image", "image_url", URL)))));
        var message = LLMAssistantMessage.builder().contents(List.of()).providerMetadata(state).build();
        assertThrows(IllegalArgumentException.class, () -> OpenAIResponsesMapper.toRequest(new LLMPrompt(message), OpenAIModelOptions.baseOptions(), "fake-key"));
    }

    /**
     * 逐个断言现有供应商 mapper 拒绝同一非法统一输入。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     */
    private static void assertAllMappersReject(LLMPrompt prompt) {
        assertThrows(IllegalArgumentException.class, () -> DeepSeekChatCompletionMapper.toRequest(prompt, LLMModelOptions.builder().build(), "https://example.test", "/chat", "fake-key"));
        assertThrows(IllegalArgumentException.class, () -> OpenAIResponsesMapper.toRequest(prompt, OpenAIModelOptions.baseOptions(), "fake-key"));
        assertThrows(IllegalArgumentException.class, () -> OpenAIChatCompletionMapper.toRequest(prompt, OpenAIChatCompletionModelOptions.baseOptions(), "fake-key"));
    }

    /**
     * 构造相同图文顺序的最小 Prompt，供多个供应商协议对照。
     *
     * @param detail 模型协议使用的图片细节级别
     * @return 满足本方法约束的 {@code LLMPrompt} 结果
     */
    private static LLMPrompt prompt(LLMImageDetail detail) {
        return new LLMPrompt(LLMUserMessage.builder().contents(LLMContentListBuilder.builder()
                .addText("first").addImage(URL, detail).addText("second").addImage(INLINE).build()).build());
    }

    /**
     * 从 mapper 输出中读取有序内容块，供测试断言真实数组结构。
     *
     * @param value 待处理的值
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> blocks(Object value) {
        assertInstanceOf(List.class, value);
        return (List<Map<String, Object>>) value;
    }
}
