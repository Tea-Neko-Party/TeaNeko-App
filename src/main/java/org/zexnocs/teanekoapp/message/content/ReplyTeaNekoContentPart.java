package org.zexnocs.teanekoapp.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.TeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;

/**
 * 回复消息内容类，表示一个回复消息的内容。
 * <p>如果 api 不同，则平台自行实现 {@link IReplyTeaNekoContentPart}</p>
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
@TeaNekoContentPart(TeaNekoContent.PREFIX + IReplyTeaNekoContentPart.TYPE)
public class ReplyTeaNekoContentPart implements IReplyTeaNekoContentPart {
    /// 回复的消息 ID
    @JsonProperty("id")
    private String id;
}