package org.zexnocs.teanekoclient.onebot.data.receive.message.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.content.IAtTeaNekoContent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;

/**
 * 符合 onebot 规范的 At 消息内容数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@AllArgsConstructor
@TeaNekoContent(OnebotMessage.PREFIX + IAtTeaNekoContent.TYPE)
public class AtOnebotContent implements IAtTeaNekoContent {
    /// 被@的用户的 QQ 号
    /// 如果 @ 全体成员，则为 "all"
    @JsonProperty("qq")
    private String qq;

    /// 被@的用户的昵称
    @JsonProperty("name")
    private String name;

    /**
     * 获取被 @ 用户的平台 ID。
     * 如果是 @ 全体成员，则为 "all"。
     *
     * @return {@link String} 被 @ 用户的平台 ID
     */
    @Override
    @JsonIgnore
    public @NonNull String getId() {
        return qq;
    }
}
