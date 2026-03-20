package org.zexnocs.teanekoapp.message.api.content;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentPart;

import java.util.List;

/**
 * 群转发消息的 Node 消息内容接口，表示一个 Node 消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
public interface INodeTeaNekoContentPart extends ITeaNekoContentPart {
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
     * @return {@link List }<{@link ? } {@link extends } {@link ITeaNekoContent }>
     */
    List<? extends ITeaNekoContent> getContents();

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
     * 默认将所有的 contents 的 raw message
     */
    @Override
    @NonNull
    default String toRawString() {
        StringBuilder sb = new StringBuilder();
        boolean flag = false;
        for(var content : getContents()) {
            if(flag) {
                sb.append("\n");
            }
            flag = true;
            sb.append("%s (%s): %s".formatted(getNickname(),
                    getUserId(),
                    content.getContentPart().toRawString()));
        }
        return sb.toString();
    }
}
