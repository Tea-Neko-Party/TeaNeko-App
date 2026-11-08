package org.zexnocs.teanekoagent.llm.framework.message;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于快速构造 LLM Message List 的 builder。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：修正不可变消息列表说明中的无效 HTML。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 * @version 4.9.0-l
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
     * 2026/11/05：使用消息类型链接描述列表，不拼接无效泛型 HTML。
     *
     * @return 由 {@link ILLMMessage} 组成的不可变消息列表
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
