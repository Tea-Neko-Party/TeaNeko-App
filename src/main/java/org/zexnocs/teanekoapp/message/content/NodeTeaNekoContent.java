package org.zexnocs.teanekoapp.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.TeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.content.INodeTeaNekoContent;

import java.util.List;

/**
 * 群转发消息的 Node 消息内容类，表示一个 Node 消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@Getter
@AllArgsConstructor
@TeaNekoContent(TeaNekoMessage.PREFIX + INodeTeaNekoContent.TYPE)
public class NodeTeaNekoContent implements INodeTeaNekoContent {
    /// 被转发消息的用户 ID
    @JsonProperty("userId")
    private final String userId;

    /// 被转发消息的昵称
    @JsonProperty("nickname")
    private final String nickname;

    /// 被转发消息的内容列表
    @JsonProperty("content")
    private final List<ITeaNekoContent> content;
}