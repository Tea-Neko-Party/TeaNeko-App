package org.zexnocs.teanekoclient.onebot.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.content.ImageTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.content.ReplyTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.content.TextTeaNekoContentPart;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.content.AtOnebotContentPart;

import java.util.ArrayList;
import java.util.List;

/**
 * 符合 onebot 规范的消息列表构造器。
 * <p>注意，该 builder 是线程不安全的。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
public class OnebotContentListBuilder implements ITeaNekoContentListBuilder {

    public static OnebotContentListBuilder builder() {
        return new OnebotContentListBuilder();
    }

    /// 消息列表，构造过程中会不断添加消息对象，最终构造完成后会返回这个列表。
    private final List<ITeaNekoContent> messageList = new ArrayList<>();

    public OnebotContentListBuilder() {}

    /**
     * 构造出一个最终结果。
     *
     * @return {@link List }<{@link ITeaNekoContent }>
     */
    @Override
    public List<ITeaNekoContent> build() {
        return messageList;
    }

    /**
     * 添加一个构造好的 {@link ITeaNekoContent}。
     *
     * @param message 已经构造好的消息对象
     * @return 当前的构造器对象，以便于链式调用
     */
    @Override
    public ITeaNekoContentListBuilder addContent(@NonNull ITeaNekoContent message) {
        // 如果是文本信息，则尝试合并文本消息，如果最后一个消息也是文本消息，则将它们合并成一个消息。
        if (message.getContentPart() instanceof TextTeaNekoContentPart newContent) {
            return addText(newContent.getText());
        }
        messageList.add(message);
        return this;
    }

    /**
     * 添加一个构造好的 {@link ITeaNekoContent} 列表。
     *
     * @param messageList 已经构造好的消息对象列表
     * @return 当前的构造器对象，以便于链式调用
     */
    @Override
    public ITeaNekoContentListBuilder addContents(@NonNull List<ITeaNekoContent> messageList) {
        for (ITeaNekoContent message : messageList) {
            addContent(message);
        }
        return this;
    }

    /**
     * 添加一个文本消息。
     *
     * @param text 文本内容
     * @return 当前的构造器对象，以便于链式调用
     */
    @Override
    public ITeaNekoContentListBuilder addText(@Nullable String text) {
        if(text == null) {
            return this;
        }
        // 合并文本消息，如果最后一个消息也是文本消息，则将它们合并成一个消息。
        if (!messageList.isEmpty() && messageList.getLast().getContentPart() instanceof TextTeaNekoContentPart lastContent) {
            lastContent.setText(lastContent.getText() + text);
            return this;
        }
        // 否则创建一个新的文本消息并添加到列表中。
        this.messageList.add(OnebotContent.builder()
                .type(TextTeaNekoContentPart.TYPE)
                .contentPart(new TextTeaNekoContentPart(text))
                .build());
        return this;
    }

    /**
     * 使用图片路径发送：
     * 1. 本地路径："file://path"，如 "file://D:/a.jpg"。
     * 2. 网络路径：网络路径，如 "http://"。
     * 3. base64："base64://"，如 "base64://base64字符串"。
     *
     * @param imageUrl 图片的 URL 地址
     * @return 当前的构造器对象，以便于链式调用
     */
    @Override
    public ITeaNekoContentListBuilder addImage(@Nullable String imageUrl) {
        if(imageUrl == null) {
            return this;
        }
        this.messageList.add(OnebotContent.builder()
                .type(ImageTeaNekoContentPart.TYPE)
                .contentPart(new ImageTeaNekoContentPart(imageUrl, imageUrl))
                .build());
        return this;
    }

    /**
     * 添加一个 at 消息。
     *
     * @param atId 被 at 的用户 ID
     * @return 当前的构造器对象，以便于链式调用
     */
    @Override
    public ITeaNekoContentListBuilder addAt(@Nullable String atId) {
        if(atId == null) {
            return this;
        }
        this.messageList.add(OnebotContent.builder()
                .type(AtOnebotContentPart.TYPE)
                .contentPart(new AtOnebotContentPart(atId, null))
                .build());
        return this;
    }

    /**
     * 添加一个 reply 消息。
     *
     * @param replyId 被回复的消息 ID
     * @return 当前的构造器对象，以便于链式调用
     */
    @Override
    public ITeaNekoContentListBuilder addReply(@Nullable String replyId) {
        if(replyId == null) {
            return this;

        }
        this.messageList.add(OnebotContent.builder()
                .type(ReplyTeaNekoContentPart.TYPE)
                .contentPart(new ReplyTeaNekoContentPart(replyId))
                .build());
        return this;
    }
}
