package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;


/**
 * 根据消息 ID 获取消息的发送器工具
 * <p>一般都会实现 {@link org.zexnocs.teanekoapp.sender.api.ISender} 接口。
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface IGetMessageSender {
    /**
     * 根据消息 ID 获取消息的发送器工具
     * 一般 future 是新创一个 future 来接收到 {@link org.zexnocs.teanekoapp.sender.interfaces.ISenderService} 的结果后使用
     * {@code .whenComplete()} 来完成这个 future。
     *
     * @param messageId 消息 ID
     * @return 一个 future，完成后会得到一个包含消息数据的结果对象
     */
    TaskFuture<ITaskResult<ITeaNekoMessageData>> sendMessageWithFuture(String messageId);
}
