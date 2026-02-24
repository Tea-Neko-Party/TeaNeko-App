package org.zexnocs.teanekoapp.message.api;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 一个快速构造 {@link java.util.List}<{@link ITeaNekoMessage}> 的工具类。
 * <p>记得处理 null 的情况，因为有些消息类型可能会返回 null，例如文本消息的文本内容可能为 null。</p>
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface ITeaNekoMessageBuilder {
    /**
     * 构造出一个最终结果。
     *
     * @return {@link List }<{@link ITeaNekoMessage }>
     */
    List<ITeaNekoMessage> build();

    /**
     * 添加一个构造好的 {@link ITeaNekoMessage}。
     *
     * @param message 已经构造好的消息对象
     * @return 当前的构造器对象，以便于链式调用
     */
    ITeaNekoMessageBuilder addMessage(@NonNull ITeaNekoMessage message);

    /**
     * 添加一个构造好的 {@link ITeaNekoMessage} 列表。
     *
     * @param messageList 已经构造好的消息对象列表
     * @return 当前的构造器对象，以便于链式调用
     */
    ITeaNekoMessageBuilder addMessages(@NonNull List<ITeaNekoMessage> messageList);

    /**
     * 添加一个文本消息。
     *
     * @param text 文本内容
     * @return 当前的构造器对象，以便于链式调用
     */
    ITeaNekoMessageBuilder addTextMessage(@Nullable String text);

    /**
     * 使用图片路径发送：
     * 1. 本地路径："file://path"，如 "file://D:/a.jpg"。
     * 2. 网络路径：网络路径，如 "http://"。
     * 3. base64："base64://"，如 "base64://base64字符串"。
     *
     * @param imageUrl 图片的 URL 地址
     * @return 当前的构造器对象，以便于链式调用
     */
    ITeaNekoMessageBuilder addImageMessage(@Nullable String imageUrl);

    /**
     * 添加一个 at 消息。
     *
     * @param atId 被 at 的用户 ID
     * @return 当前的构造器对象，以便于链式调用
     */
    ITeaNekoMessageBuilder addAtMessage(@Nullable String atId);

    /**
     * 添加一个 reply 消息。
     *
     * @param replyId 被回复的消息 ID
     * @return 当前的构造器对象，以便于链式调用
     */
    ITeaNekoMessageBuilder addReplyMessage(@Nullable String replyId);
}
