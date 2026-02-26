package org.zexnocs.teanekoapp.message.content;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

/**
 * At消息内容类，表示一个At消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.9
 */
@AllArgsConstructor
public class TeaNekoAtContent implements ITeaNekoContent {

    /// 被 @ 用户的平台 ID
    private final String id;

    ///被 @ 用户的昵称
    private final String name;

    /**
     * 转化成命令解析的字符串表示。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    public @NonNull String[] toCommandArgs() {
        return new String[]{id};
    }
}
