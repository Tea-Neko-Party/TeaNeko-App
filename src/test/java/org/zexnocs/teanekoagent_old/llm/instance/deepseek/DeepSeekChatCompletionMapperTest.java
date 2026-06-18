package org.zexnocs.teanekoagent_old.llm.instance.deepseek;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelOptions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completion 请求与响应映射测试。
 * <br>验证参与者名称、可选请求字段和供应商推理内容的映射边界。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class DeepSeekChatCompletionMapperTest {
    /**
     * 验证非空参与者名称会写入 system、user 和 assistant 请求消息。
     */
    @Test
    void toRequestIncludesParticipantNames() {
        var messages = List.<ILLMMessage>of(
                LLMSystemMessage.builder()
                        .name("policy")
                        .contents(text("system"))
                        .build(),
                LLMUserMessage.builder()
                        .name("alice")
                        .contents(text("user"))
                        .build(),
                LLMAssistantMessage.builder()
                        .name("assistant-a")
                        .contents(text("assistant"))
                        .build()
        );

        var request = DeepSeekChatCompletionMapper.toRequest(
                new LLMPrompt(messages),
                LLMModelOptions.empty(),
                "https://example.test",
                "/chat/completions",
                "test-key"
        );

        Assertions.assertEquals("policy", request.getMessages().get(0).get("name"));
        Assertions.assertEquals("alice", request.getMessages().get(1).get("name"));
        Assertions.assertEquals("assistant-a", request.getMessages().get(2).get("name"));
    }

    /**
     * 验证空值、空字符串和纯空白参与者名称不会写入请求消息。
     */
    @Test
    void toRequestOmitsBlankParticipantNames() {
        var messages = List.<ILLMMessage>of(
                LLMSystemMessage.builder()
                        .contents(text("system"))
                        .build(),
                LLMUserMessage.builder()
                        .name("")
                        .contents(text("user"))
                        .build(),
                LLMAssistantMessage.builder()
                        .name("   ")
                        .contents(text("assistant"))
                        .build()
        );

        var request = DeepSeekChatCompletionMapper.toRequest(
                new LLMPrompt(messages),
                LLMModelOptions.empty(),
                "https://example.test",
                "/chat/completions",
                "test-key"
        );

        Assertions.assertFalse(request.getMessages().get(0).containsKey("name"));
        Assertions.assertFalse(request.getMessages().get(1).containsKey("name"));
        Assertions.assertFalse(request.getMessages().get(2).containsKey("name"));
    }

    /**
     * 验证请求 JSON 会忽略无效的空可选字段，同时保留有效 metadata 字段。
     */
    @Test
    void toRequestJsonOmitsEmptyOptionalFields() {
        var messages = List.<ILLMMessage>of(
                LLMUserMessage.builder()
                        .name("")
                        .contents(text("user"))
                        .build()
        );
        var options = LLMModelOptions.builder()
                .stopWords(List.of())
                .toolChoice("")
                .metadata(Map.of(
                        "body.emptyText", "",
                        "body.emptyList", List.of(),
                        "body.keep", "value"
                ))
                .build();

        var request = DeepSeekChatCompletionMapper.toRequest(
                new LLMPrompt(messages),
                options,
                "https://example.test",
                "/chat/completions",
                "test-key"
        );

        var json = new ObjectMapper().writeValueAsString(request);
        Assertions.assertFalse(json.contains("\"name\""));
        Assertions.assertFalse(json.contains("\"stop\""));
        Assertions.assertFalse(json.contains("\"tool_choice\""));
        Assertions.assertFalse(json.contains("\"emptyText\""));
        Assertions.assertFalse(json.contains("\"emptyList\""));
        Assertions.assertTrue(json.contains("\"keep\""));
    }

    /**
     * 验证 DeepSeek 响应中的 assistant 参与者名称能够映射到统一消息对象。
     */
    @Test
    void toResultParsesAssistantMessageName() {
        var response = new DeepSeekChatCompletionResponseData();
        var choice = new DeepSeekChatCompletionResponseData.Choice();
        var message = new DeepSeekChatCompletionResponseData.Message();
        message.setRole("assistant");
        message.setName("assistant-a");
        message.setContent("hello");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        var result = DeepSeekChatCompletionMapper.toResult(response);

        var mappedMessage = result.getFirstMessage().orElseThrow();
        Assertions.assertEquals("assistant-a", mappedMessage.getName());
    }

    /**
     * 验证响应未提供 assistant 参与者名称时，统一消息使用空字符串默认值。
     */
    @Test
    void toResultDefaultsMissingAssistantMessageNameToEmpty() {
        var response = new DeepSeekChatCompletionResponseData();
        var choice = new DeepSeekChatCompletionResponseData.Choice();
        var message = new DeepSeekChatCompletionResponseData.Message();
        message.setRole("assistant");
        message.setContent("hello");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        var result = DeepSeekChatCompletionMapper.toResult(response);

        var mappedMessage = result.getFirstMessage().orElseThrow();
        Assertions.assertEquals("", mappedMessage.getName());
    }

    /**
     * 验证供应商内部推理内容不会作为普通 assistant 正文暴露给上层 Agent。
     */
    @Test
    void toResultDoesNotExposeProviderReasoningAsAssistantContent() {
        var response = new DeepSeekChatCompletionResponseData();
        var choice = new DeepSeekChatCompletionResponseData.Choice();
        var message = new DeepSeekChatCompletionResponseData.Message();
        message.setRole("assistant");
        message.setReasoningContent("private provider reasoning");
        choice.setMessage(message);
        response.setChoices(List.of(choice));

        var result = DeepSeekChatCompletionMapper.toResult(response);
        var mappedMessage = result.getFirstMessage().orElseThrow();
        var mappedText = mappedMessage.getContents().stream()
                .map(ILLMContent::getContentPart)
                .filter(TextLLMContentPart.class::isInstance)
                .map(TextLLMContentPart.class::cast)
                .map(TextLLMContentPart::getText)
                .findFirst()
                .orElse("");

        Assertions.assertEquals("", mappedText);
    }

    /**
     * 构造只包含一个文本片段的 LLM Content 列表。
     *
     * @param text 文本内容
     * @return 不可变的 LLM Content 列表
     */
    private static List<ILLMContent> text(String text) {
        return LLMContentListBuilder.builder()
                .addText(text)
                .build();
    }
}
