package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.response.api.IGroupMemberResponseData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

/**
 * 获取群成员信息的发送器。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
public interface IGetGroupMemberInfoSender {
    /**
     * 获取群成员信息。
     *
     * @param token 发送器发送环境的标识符
     * @param groupId 群号
     * @param userId 成员QQ号
     */
    TaskFuture<? extends IGroupMemberResponseData> get(String token, String groupId, String userId);
}
