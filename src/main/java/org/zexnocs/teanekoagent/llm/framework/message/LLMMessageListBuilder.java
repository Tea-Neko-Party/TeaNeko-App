package org.zexnocs.teanekoagent.llm.framework.message;

import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于快速构造 LLM Message List 的 builder。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public class LLMMessageListBuilder {
    /**
     * 直接获取一个 builder。
     *
     * @return {@link LLMMessageListBuilder }
     */
    public static LLMMessageListBuilder builder() {
        return new LLMMessageListBuilder();
    }

    /// 存储的 message list
    private final List<ILLMMessage> messageList = new ArrayList<>();

    /**
     * 构造出不可变 list。
     *
     * @return {@link List }<{@link ILLMMessage }>
     */
    public List<ILLMMessage> build() {
        return List.copyOf(messageList);
    }

    /**
     * 直接添加一个 message。
     *
     * @param message message
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder add(ILLMMessage message) {
        messageList.add(message);
        return this;
    }

    /**
     * 直接添加一个 message。
     *
     * @param message message
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addMessage(ILLMMessage message) {
        return add(message);
    }

    /**
     * 用单个 text content 添加 system message。
     *
     * @param text 文本内容
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addSystem(String text) {
        return addSystem("", text);
    }

    /**
     * 用单个 text content 添加带参与者名称的 system message。
     *
     * @param name 参与者名称
     * @param text 文本内容
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addSystem(String name, String text) {
        return addSystem(name, textContent(text));
    }

    /**
     * 用 content list 添加 system message。
     *
     * @param contents content list
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addSystem(List<? extends ILLMContent> contents) {
        return addSystem("", contents);
    }

    /**
     * 用 content list 添加带参与者名称的 system message。
     *
     * @param name 参与者名称
     * @param contents content list
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addSystem(String name, List<? extends ILLMContent> contents) {
        return add(LLMSystemMessage.builder()
                .name(normalizeName(name))
                .contents(copyContents(contents))
                .build());
    }

    /**
     * 用单个 text content 添加 user message。
     *
     * @param text 文本内容
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addUser(String text) {
        return addUser("", text);
    }

    /**
     * 用单个 text content 添加带参与者名称的 user message。
     *
     * @param name 参与者名称
     * @param text 文本内容
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addUser(String name, String text) {
        return addUser(name, textContent(text));
    }

    /**
     * 用 content list 添加 user message。
     *
     * @param contents content list
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addUser(List<? extends ILLMContent> contents) {
        return addUser("", contents);
    }

    /**
     * 用 content list 添加带参与者名称的 user message。
     *
     * @param name 参与者名称
     * @param contents content list
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addUser(String name, List<? extends ILLMContent> contents) {
        return add(LLMUserMessage.builder()
                .name(normalizeName(name))
                .contents(copyContents(contents))
                .build());
    }

    /**
     * 用单个 text content 添加 assistant message。
     *
     * @param text 文本内容
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addAssistant(String text) {
        return addAssistant("", text);
    }

    /**
     * 用单个 text content 添加带参与者名称的 assistant message。
     *
     * @param name 参与者名称
     * @param text 文本内容
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addAssistant(String name, String text) {
        return addAssistant(name, textContent(text));
    }

    /**
     * 用 content list 添加 assistant message。
     *
     * @param contents content list
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addAssistant(List<? extends ILLMContent> contents) {
        return addAssistant("", contents);
    }

    /**
     * 用 content list 添加带参与者名称的 assistant message。
     *
     * @param name 参与者名称
     * @param contents content list
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addAssistant(String name, List<? extends ILLMContent> contents) {
        return add(LLMAssistantMessage.builder()
                .name(normalizeName(name))
                .contents(copyContents(contents))
                .build());
    }

    /**
     * 用 tool call id 和结果文本添加 tool message。
     *
     * @param toolCallId tool call id
     * @param result 工具执行结果文本
     * @return 当前 builder 对象，以便链式调用
     */
    public LLMMessageListBuilder addTool(String toolCallId, String result) {
        return add(LLMToolMessage.builder()
                .toolCallId(toolCallId)
                .contents(textContent(result))
                .build());
    }

    private static List<ILLMContent> textContent(String text) {
        return LLMContentListBuilder.builder()
                .addText(text)
                .build();
    }

    private static List<ILLMContent> copyContents(List<? extends ILLMContent> contents) {
        return contents == null ? List.of() : List.copyOf(contents);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name;
    }
}
