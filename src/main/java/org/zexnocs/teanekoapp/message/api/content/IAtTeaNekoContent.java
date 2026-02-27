package org.zexnocs.teanekoapp.message.api.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

/**
 * At消息内容接口，表示一个At消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
public interface IAtTeaNekoContent extends ITeaNekoContent {
    /// 类型字符串常量。
    String TYPE = "at";

    /**
     * 获取被 @ 用户的平台 ID。
     * 如果是 @ 全体成员，则为 "all"。
     *
     * @return {@link String} 被 @ 用户的平台 ID
     */
    @NonNull String getId();

    /**
     * 获取类型。
     * 该方法应当加上 {@link com.fasterxml.jackson.annotation.JsonIgnore} 注解防止被序列化。
     *
     * @return {@link String} 类型字符串
     */
    @NonNull
    @JsonIgnore
    @Override
    default String getType() {
        return TYPE;
    }

    /**
     * 转化成命令解析的字符串表示。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    @NonNull
    default String[] toCommandArgs() {
        return new String[]{getId()};
    }
}
