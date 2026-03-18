package org.zexnocs.teanekoapp.client.tools;

/**
 * 群组踢出发送器。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
public interface IGroupKickSender {
    /**
     * 将用户踢出群组。
     *
     * @param groupId 群组平台 ID
     * @param userId  用户平台 ID
     */
    void kick(String groupId, String userId);
}
