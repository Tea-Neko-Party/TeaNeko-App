package org.zexnocs.teanekoapp.message.api.content;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

/**
 * 文本消息内容接口，表示一个文本消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
public interface ITextTeaNekoContent extends ITeaNekoContent {
    /// 类型字符串常量。
    String TYPE = "text";

    /**
     * 获取文本内容。
     *
     * @return {@link String} 文本内容
     */
    @NonNull String getText();

    /**
     * 转化成命令解析的字符串表示。
     * <p>默认按空白切分，空文本返回空数组。</p>
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    @NonNull
    default String[] toCommandArgs() {
        var text = getText().trim();
        if(text.isEmpty()) {
            return new String[0];
        }
        return text.split("\\s+");
    }
}
