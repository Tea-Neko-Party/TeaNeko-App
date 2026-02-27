package org.zexnocs.teanekoapp.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.TeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.content.IAtTeaNekoContent;

/**
 * At消息内容类，表示一个At消息的内容。
 * <p>如果 api 不同，则平台自行实现 {@link IAtTeaNekoContent}
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.9
 */
@Getter
@AllArgsConstructor
@TeaNekoContent(TeaNekoMessage.PREFIX + IAtTeaNekoContent.TYPE)
public class AtTeaNekoContent implements IAtTeaNekoContent {
    /// 被 @ 用户的平台 ID
    /// 如果是 @ 全体成员，则为 "all"
    @JsonProperty("id")
    private final String id;

    ///被 @ 用户的昵称
    @JsonProperty("name")
    private final String name;
}
