package org.zexnocs.teanekoclient.onebot.data.response.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
public class GroupMemberResponseData {
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
    private int age;

    /// 地域
    @JsonProperty("area")
    private String area;

    /// 加入时间
    @JsonProperty("join_time")
    private long joinTime;

    /// 上次发言时间
    @JsonProperty("last_sent_time")
    private long lastSentTime;

    /// 群等级
    @JsonProperty("level")
    private int level;

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
}
