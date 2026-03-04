package org.zexnocs.teanekoclient.onebot.sender.message;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.DeleteMessageSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

/**
 * 撤回消息发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@SuppressWarnings("rawtypes")
@Component("DeleteMessageSender")
public class DeleteMessageSender extends AbstractOnebotSender<DeleteMessageSendParamsData, Map> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public DeleteMessageSender(ISenderService senderService,
                               OnebotClient client,
                               @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 撤回消息
     *
     * @param token     发送器发送环境的标识符
     * @param messageId 消息ID
     */
    public void delete(String token, long messageId) {
        send(token,
            DeleteMessageSendParamsData.builder()
                    .messageId(messageId)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }
}
