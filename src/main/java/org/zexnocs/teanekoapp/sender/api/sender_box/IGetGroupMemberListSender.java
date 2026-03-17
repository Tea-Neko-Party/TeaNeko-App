package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.response.api.IGroupMemberResponseData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.util.List;

/**
 * 获取群组中所有群成员的 sender
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
public interface IGetGroupMemberListSender {
    /**
     * 获取所有群成员信息。
     *
     * @param groupId 群号
     */
    TaskFuture<List<? extends IGroupMemberResponseData>> get(String groupId);
}
