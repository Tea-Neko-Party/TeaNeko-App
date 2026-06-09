package org.zexnocs.teanekoagent.llm.framework.message;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.*;

import java.util.List;

class LLMMessageListBuilderTest {
    @Test
    void buildsMessagesFromTextAndContentList() {
        var assistantContents = LLMContentListBuilder.builder()
                .addText("assistant content")
                .build();

        var messages = LLMMessageListBuilder.builder()
                .addSystem("policy", "system text")
                .addUser("alice", "user text")
                .addAssistant("assistant-a", assistantContents)
                .build();

        Assertions.assertEquals(3, messages.size());
        assertMessage(messages.get(0), LLMMessageRole.SYSTEM, "policy", "system text");
        assertMessage(messages.get(1), LLMMessageRole.USER, "alice", "user text");
        assertMessage(messages.get(2), LLMMessageRole.ASSISTANT, "assistant-a", "assistant content");
        Assertions.assertInstanceOf(ILLMAssistantMessage.class, messages.get(2));
    }

    @Test
    void addsExistingMessageAndToolResult() {
        var existing = LLMUserMessage.builder()
                .name("bob")
                .contents(LLMContentListBuilder.builder()
                        .addText("existing")
                        .build())
                .build();

        var messages = LLMMessageListBuilder.builder()
                .addMessage(existing)
                .addTool("call-1", "tool result")
                .build();

        Assertions.assertSame(existing, messages.get(0));
        Assertions.assertEquals(LLMMessageRole.TOOL, messages.get(1).getRole());
        Assertions.assertEquals("tool result", text(messages.get(1).getContents()));
        var toolMessage = Assertions.assertInstanceOf(ILLMToolMessage.class, messages.get(1));
        Assertions.assertEquals("call-1", toolMessage.getToolCallId());
    }

    @Test
    void defaultsNameToEmpty() {
        var messages = LLMMessageListBuilder.builder()
                .addSystem("system")
                .addUser(List.of())
                .addAssistant("assistant")
                .addSystem(null, "system")
                .build();

        Assertions.assertEquals("", messages.get(0).getName());
        Assertions.assertEquals("", messages.get(1).getName());
        Assertions.assertEquals("", messages.get(2).getName());
        Assertions.assertEquals("", messages.get(3).getName());
    }

    private static void assertMessage(ILLMMessage message, LLMMessageRole role, String name, String text) {
        Assertions.assertEquals(role, message.getRole());
        Assertions.assertEquals(name, message.getName());
        Assertions.assertEquals(text, text(message.getContents()));
    }

    private static String text(List<ILLMContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        var contentPart = contents.getFirst().getContentPart();
        if (contentPart instanceof TextLLMContentPart textPart) {
            return textPart.getText();
        }
        return "";
    }
}
