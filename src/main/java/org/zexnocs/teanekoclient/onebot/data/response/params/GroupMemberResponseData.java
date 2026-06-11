package org.zexnocs.teanekoclient.onebot.data.response.params;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.response.api.IGroupMemberResponseData;

import java.time.Instant;

/**
 * 群成员信息响应子数据
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponseData implements IGroupMemberResponseData {
    /// 群号
    @JsonProperty("group_id")
    private long groupId;

    /// 用户 id
    @JsonProperty("user_id")
    private long userId;

    /// qq昵称
    @JsonProperty("nickname")
    private String nickname;

    /// 群内名片
    @JsonProperty("card")
    private String card;

    /// 性别
    @JsonProperty("sex")
    private String sex;

    /// 年龄
    @JsonProperty("age")
    private Integer age;

    /// 地域
    @JsonProperty("area")
    private String area;

    /// 加入时间
    @JsonProperty("join_time")
    private Long joinTime;

    /// 上次发言时间
    @JsonProperty("last_sent_time")
    private Long lastSentTime;

    /// 群等级
    @JsonProperty("level")
    private Integer level;

    /// 群内的角色
    @JsonProperty("role")
    private String role;

    /// 是否是好友。如果为 false，表示不是好友
    @JsonProperty("unfriendly")
    private boolean unfriendly;

    /// 头衔
    @JsonProperty("title")
    private String title;

    /// 头衔过期时间
    @JsonProperty("title_expire_time")
    private long titleExpireTime;

    /// 是否可以更改群名片
    @JsonProperty("card_changeable")
    private boolean cardChangeable;

    /**
     * 用户 ID
     */
    @Override
    public String getUserId() {
        return String.valueOf(userId);
    }

    /**
     * 加入时间点
     *
     * @return 加入时间点
     */
    @Override
    public @Nullable Instant getJoinInstant() {
        return joinTime == null ? null : Instant.ofEpochSecond(joinTime);
    }

    /**
     * 上次发言时间点
     *
     * @return 上次发言时间点
     */
    @Override
    public @Nullable Instant getLastSentInstant() {
        return lastSentTime == null ? null : Instant.ofEpochSecond(lastSentTime);
    }
}
