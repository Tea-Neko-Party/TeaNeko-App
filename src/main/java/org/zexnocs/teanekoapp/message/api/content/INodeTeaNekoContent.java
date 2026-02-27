package org.zexnocs.teanekoapp.message.api.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

import java.util.List;

/**
 * 群转发消息的 Node 消息内容接口，表示一个 Node 消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
public interface INodeTeaNekoContent extends ITeaNekoContent {
    /**
     * 类型字符串常量。
     */
    String TYPE = "node";

    /**
     * 获取被转发消息的用户 ID。
     *
     * @return {@link String} 被转发消息的用户 ID
     */
    String getUserId();

    /**
     * 获取被转发消息的昵称。
     *
     * @return {@link String} 被转发消息的昵称
     */
    String getNickname();

    /**
     * 获取被转发消息的内容。
     *
     * @return {@link List }<{@link ITeaNekoContent }> 被转发消息的内容列表
     */
    List<ITeaNekoContent> getContent();

    /**
     * 默认不转化成指令。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @NonNull
    default String[] toCommandArgs() {
        return new String[0];
    }

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
}
