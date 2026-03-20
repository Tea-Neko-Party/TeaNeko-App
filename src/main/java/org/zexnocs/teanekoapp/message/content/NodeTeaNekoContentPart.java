package org.zexnocs.teanekoapp.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.TeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.INodeTeaNekoContentPart;

import java.util.List;

/**
 * 群转发消息的 Node 消息内容类，表示一个 Node 消息的内容。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TeaNekoContentPart(TeaNekoContent.PREFIX + INodeTeaNekoContentPart.TYPE)
public class NodeTeaNekoContentPart implements INodeTeaNekoContentPart {
    /// 被转发消息的用户 ID
    @JsonProperty("userId")
    private String userId;

    /// 被转发消息的昵称
    @JsonProperty("nickname")
    private String nickname;

    /// 被转发消息的内容列表
    @JsonProperty("content")
    private List<? extends ITeaNekoContent> contents;
}