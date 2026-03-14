package org.zexnocs.teanekoclient.onebot.sender.private_;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.private_.LikeSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 给用户点赞发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@SuppressWarnings("rawtypes")
@Component("LikeSender")
public class LikeSender extends AbstractOnebotSender<LikeSendParamsData, Map> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public LikeSender(ISenderService senderService,
                      OnebotClient client,
                      @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 给用户点赞
     *
     * @param userId 用户ID
     * @param times  点赞次数
     * @return 点赞结果的任务未来对象，完成时包含一个列表，每个元素是一个包含点赞结果信息的映射
     */
    public TaskFuture<ITaskResult<List<Map>>> like(long userId, int times) {
        return sendWithFuture(
                LikeSendParamsData.builder()
                    .userId(userId)
                    .times(times)
                    .build(),
            Duration.ZERO,
            0,
            Duration.ofMillis(200));
    }
}
