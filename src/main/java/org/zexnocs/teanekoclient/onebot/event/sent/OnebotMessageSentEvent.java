package org.zexnocs.teanekoclient.onebot.event.sent;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;
import org.zexnocs.teanekoclient.onebot.data.send.OnebotSendData;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;

/**
 * 发送 onebot message 事件
 *
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.3.2
 */
@Getter
public class OnebotMessageSentEvent<S extends ISendParamsData<OnebotMessageSendResponseData>>
        extends OnebotSentEvent<S, OnebotMessageSendResponseData> {
    /// 发送区域的 token
    private final String token;

    /// 如果是回复的数据，则包含该数据
    @Nullable
    private final ITeaNekoMessageData messageData;

    /**
     * 事件的构造函数。
     *
     * @param data 事件数据
     */
    public OnebotMessageSentEvent(String token,
                                  @Nullable OnebotSendData<S, OnebotMessageSendResponseData> data,
                                  @Nullable ITeaNekoMessageData messageData) {
        super(data);
        this.token = token;
        this.messageData = messageData;
    }

    /**
     * 安全地设置事件被取消
     *
     * @param taskService 用于取消事件监听。
     */
    public void safeSetCancelled(ITaskService taskService) {
        setCancelled(true);
        // 使用一个假的 result 完成
        taskService.complete(getTaskKey(), new TaskResult<>(true ,null));
    }
}
