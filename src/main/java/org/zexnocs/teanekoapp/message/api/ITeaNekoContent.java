package org.zexnocs.teanekoapp.message.api;

import org.jspecify.annotations.NonNull;

/**
 * Tea Neko 消息内容接口。
 *
 * @author zExNocs
 * @date 2026/02/21
 */
public interface ITeaNekoContent {
    /**
     * 转化成命令解析的字符串表示。
     * 例如 text 文字可以根据空格切割成多个字符串。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @NonNull
    String[] toCommandArgs();
}
