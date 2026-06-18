package org.zexnocs.teanekoagent_old.llm.framework.message;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.*;

import java.util.List;

/**
 * LLM Message 列表构造器测试。
 * <br>验证不同正文来源、参与者名称、现有消息和工具结果的构造行为。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class LLMMessageListBuilderTest {
    /**
     * 验证构造器能够使用文本和 Content 列表创建不同角色的消息。
     */
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

    /**
     * 验证构造器保留直接加入的消息实例，并正确创建带调用 ID 的工具结果消息。
     */
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

    /**
     * 验证未设置或传入空值的参与者名称会统一规范化为空字符串。
     */
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

    /**
     * 断言消息的角色、参与者名称和首个文本内容符合预期。
     *
     * @param message 待检查消息
     * @param role 预期角色
     * @param name 预期参与者名称
     * @param text 预期文本内容
     */
    private static void assertMessage(ILLMMessage message, LLMMessageRole role, String name, String text) {
        Assertions.assertEquals(role, message.getRole());
        Assertions.assertEquals(name, message.getName());
        Assertions.assertEquals(text, text(message.getContents()));
    }

    /**
     * 提取 Content 列表首项中的文本，无法提取时返回空字符串。
     *
     * @param contents Content 列表
     * @return 首个文本内容或空字符串
     */
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
