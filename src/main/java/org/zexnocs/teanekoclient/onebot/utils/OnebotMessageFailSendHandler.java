package org.zexnocs.teanekoclient.onebot.utils;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.easydata.debug.DebugEasyData;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 用于处理 Onebot 消息发送失败的工具类，提供相关方法来处理发送失败的情况，例如重试发送、记录日志等。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Service
public class OnebotMessageFailSendHandler {

    private final ObjectMapper objectMapper;

    public OnebotMessageFailSendHandler(@Qualifier("jacksonJsonMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将失败记录到数据库等。
     *
     * @param namespace 记录的命名空间，可以用于区分不同类型的记录，例如 "PrivateForwardMessageSender" 等
     * @param future 发送消息的 future，可以通过该 future 来获取发送结果或者进行后续操作
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link OnebotMessageSendResponseData }>>>
     *     发送结果的 future，可以通过该 future 来获取发送结果或者进行后续操作
     */
    public TaskFuture<ITaskResult<List<OnebotMessageSendResponseData>>> recordFailed(
            String namespace,
            List<? extends ITeaNekoMessage> messages,
            TaskFuture<ITaskResult<List<OnebotMessageSendResponseData>>> future) {
        return future.whenComplete((result, throwable) -> {
            if(throwable == null && result.isSuccess()) {
                // 成功不需要记录
                return;
            }
            var dto = DebugEasyData.of(namespace).get("fail_send");
            var task = dto.getTaskConfig(namespace + "发送失败记录任务");
            var str = objectMapper.writeValueAsString(messages);
            // 获取当前的时间戳，使用 yyyy-MM-dd HH:mm:ss 格式
            String formattedDate = ChinaDateUtil.Instance.getNowDateTimeString();
            task.set(formattedDate, str).push();
        });
    }
}
