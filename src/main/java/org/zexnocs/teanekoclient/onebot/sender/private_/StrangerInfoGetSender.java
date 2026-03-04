package org.zexnocs.teanekoclient.onebot.sender.private_;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserGetSender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.StrangerInfoGetResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.private_.StrangerInfoParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 获取陌生人信息发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Component("StrangerInfoGetSender")
public class StrangerInfoGetSender extends AbstractOnebotSender<StrangerInfoParamsData, StrangerInfoGetResponseData>
        implements IPlatformUserGetSender {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public StrangerInfoGetSender(ISenderService senderService,
                                 OnebotClient client,
                                 @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 获取陌生人信息
     *
     * @param token  发送器发送环境的标识符
     * @param userId 用户ID
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link StrangerInfoGetResponseData }>>>
     */
    public TaskFuture<StrangerInfoGetResponseData> getPlatformUserInfo(String token, String userId) {
        return sendWithFuture(token,
                StrangerInfoParamsData.builder()
                    .userId(Long.parseLong(userId))
                    .noCache(true)
                    .build(),
                Duration.ZERO,
                8,
                Duration.ofMillis(100))
                .thenApply(r -> {
                    if(r.isSuccess()) {
                        return r.getResult().getFirst();
                    } else {
                        return null;
                    }
                });
    }
}
