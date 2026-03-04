package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupListResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GetGroupListParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 获取群列表发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Component
public class GetGroupListSender extends AbstractOnebotSender<GetGroupListParamsData, GroupListResponseData> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public GetGroupListSender(ISenderService senderService,
                              OnebotClient client,
                              @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 获取群列表。
     *
     * @param token 发送器发送环境的标识符
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link GroupListResponseData }>>>
     */
    public TaskFuture<ITaskResult<List<GroupListResponseData>>> get(String token) {
        return sendWithFuture(
                token,
                GetGroupListParamsData.builder()
                    .noCache(true)
                    .build(),
                Duration.ZERO,
                3,
                Duration.ofMillis(200)
        );
    }
}
