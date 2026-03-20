package org.zexnocs.teanekoapp.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.TeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IAtTeaNekoContentPart;

/**
 * At消息内容类，表示一个At消息的内容。
 * <p>如果 api 不同，则平台自行实现 {@link IAtTeaNekoContentPart}
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
@TeaNekoContentPart(TeaNekoContent.PREFIX + IAtTeaNekoContentPart.TYPE)
public class AtTeaNekoContentPart implements IAtTeaNekoContentPart {
    /// 被 @ 用户的平台 ID
    /// 如果是 @ 全体成员，则为 "all"
    @JsonProperty("id")
    private String id;

    ///被 @ 用户的昵称
    @JsonProperty("name")
    private String name;
}
