package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;

import java.util.List;

/**
 * 用于构建 node 信息的接口。
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface IForwardMessageBuilder {
    /**
     * 发送转发消息。
     *
     */
    void send();

    /**
     * 以分段的方式发送消息。
     *
     * @param partSize 每段的大小
     */
    void sendByPart(int partSize);

    /**
     * 使用 bot 作为发送者发送该消息。
     *
     * @param message 消息内容
     * @return {@link IForwardMessageBuilder }
     */
    IForwardMessageBuilder addBotText(String message);

    /**
     * 添加一群文本
     *
     * @param textList 消息内容列表
     * @return {@link IForwardMessageBuilder }
     */
    IForwardMessageBuilder addBotAllText(List<String> textList);

    /**
     * 添加一个消息
     *
     * @param messageList 消息内容列表
     * @return 当前构造器实例
     */
    IForwardMessageBuilder addBotList(List<ITeaNekoMessage> messageList);

    /**
     * 添加一群消息
     *
     * @param messageListList 消息内容列表
     * @return {@link IForwardMessageBuilder }
     */
    IForwardMessageBuilder addBotAllList(List<List<ITeaNekoMessage>> messageListList);

    /**
     * 添加一个简单的消息
     *
     * @param userId   用户 ID
     * @param nickname 用户昵称
     * @param message  消息内容
     * @return {@link IForwardMessageBuilder }
     */
    IForwardMessageBuilder addText(String userId, String nickname, String message);

    /**
     * 添加一群文本
     *
     * @param userId   用户 ID
     * @param nickname 用户昵称
     * @param textList 消息内容列表
     * @return {@link IForwardMessageBuilder }
     */
    IForwardMessageBuilder addAllText(String userId, String nickname, List<String> textList);

    /**
     * 添加一个消息
     *
     * @param userId      用户 ID
     * @param nickname    用户昵称
     * @param messageList 消息内容列表
     * @return 当前构造器实例
     */
    IForwardMessageBuilder addList(String userId, String nickname, List<ITeaNekoMessage> messageList);

    /**
     * 添加一群消息
     *
     * @param userId          用户 ID
     * @param nickname        用户昵称
     * @param messageListList 消息内容列表
     * @return {@link IForwardMessageBuilder }
     */
    IForwardMessageBuilder addAllList(String userId, String nickname, List<List<ITeaNekoMessage>> messageListList);
}
