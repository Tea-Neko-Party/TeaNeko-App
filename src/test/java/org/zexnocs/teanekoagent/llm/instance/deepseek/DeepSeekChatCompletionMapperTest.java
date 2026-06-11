package org.zexnocs.teanekoagent.llm.instance.deepseek;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class DeepSeekChatCompletionMapperTest {
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

    private static List<ILLMContent> text(String text) {
        return LLMContentListBuilder.builder()
                .addText(text)
                .build();
    }
}
