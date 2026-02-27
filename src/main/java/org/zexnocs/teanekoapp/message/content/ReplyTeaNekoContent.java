package org.zexnocs.teanekoapp.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.TeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContent;

/**
 * 回复消息内容类，表示一个回复消息的内容。
 * <p>如果 api 不同，则平台自行实现 {@link IReplyTeaNekoContent}</p>
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@Getter
@AllArgsConstructor
@TeaNekoContent(TeaNekoMessage.PREFIX + IReplyTeaNekoContent.TYPE)
public class ReplyTeaNekoContent implements IReplyTeaNekoContent {
    /// 回复的消息 ID
    @JsonProperty("id")
    private final String id;
}