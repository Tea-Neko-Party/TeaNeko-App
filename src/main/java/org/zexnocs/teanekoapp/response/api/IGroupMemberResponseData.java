package org.zexnocs.teanekoapp.response.api;

import org.jspecify.annotations.Nullable;

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
     * 加入时间 (单位毫秒)
     *
     * @return {@link Long }
     */
    @Nullable
    Long getJoinTimeMs();

    /**
     * 上次发言时间 (单位秒)
     *
     * @return {@link Long }
     */
    @Nullable
    Long getLastSentTimeMs();

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
