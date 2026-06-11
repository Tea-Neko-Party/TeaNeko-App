package org.zexnocs.teanekoapp.response.api;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 群成员信息响应子数据接口。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
public interface IGroupMemberResponseData {
    /**
     * 用户昵称
     *
     * @return {@link String }
     */
    @Nullable
    String getNickname();

    /**
     * 用户 ID
     *
     * @return {@link String }
     */
    @Nullable
    String getUserId();

    /**
     * 群昵称
     *
     * @return {@link String }
     */
    @Nullable
    String getCard();

    /**
     * 加入时间点
     *
     * @return 加入时间点
     */
    @Nullable
    Instant getJoinInstant();

    /**
     * 上次发言时间点
     *
     * @return 上次发言时间点
     */
    @Nullable
    Instant getLastSentInstant();

    /**
     * 获取加入时间的 Unix 毫秒时间戳。
     *
     * @return 毫秒时间戳
     * @deprecated 业务代码应直接使用 {@link #getJoinInstant()}
    */
    @Deprecated
    default @Nullable Long getJoinTimeMs() {
        var instant = getJoinInstant();
        return instant == null ? null : instant.toEpochMilli();
    }

    /**
     * 获取最后发言时间的 Unix 毫秒时间戳。
     *
     * @return 毫秒时间戳
     * @deprecated 业务代码应直接使用 {@link #getLastSentInstant()}
    */
    @Deprecated
    default @Nullable Long getLastSentTimeMs() {
        var instant = getLastSentInstant();
        return instant == null ? null : instant.toEpochMilli();
    }

    /**
     * 群等级
     *
     * @return {@link Integer }
     */
    @Nullable
    Integer getLevel();

    /**
     * 头衔
     *
     * @return {@link String }
     */
    @Nullable
    String getTitle();

    /**
     * 群内的角色
     *
     * @return {@link String }
     */
    @Nullable
    String getRole();
}
