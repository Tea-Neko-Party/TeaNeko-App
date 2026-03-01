package org.zexnocs.teanekoapp.message.api.content;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

/**
 * 回复消息内容接口，表示一个回复消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
public interface IReplyTeaNekoContent extends ITeaNekoContent {
    /// 类型字符串常量。
    String TYPE = "reply";

    /**
     * 获取回复的消息 ID。
     *
     * @return {@link String} 回复的消息 ID
     */
    @NonNull String getId();

    /**
     * 转化成命令解析的字符串表示。
     * <p>reply 默认不参与指令解析。</p>
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    @NonNull
    default String[] toCommandArgs() {
        return new String[0];
    }
}
