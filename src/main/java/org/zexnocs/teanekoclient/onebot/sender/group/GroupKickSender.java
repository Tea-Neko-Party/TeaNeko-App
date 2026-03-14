package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GroupKickSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

/**
 * 踢出群成员发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@SuppressWarnings("rawtypes")
@Component("GroupKickSender")
public class GroupKickSender extends AbstractOnebotSender<GroupKickSendParamsData, Map> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public GroupKickSender(ISenderService senderService,
                           OnebotClient client,
                           @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 踢出群成员。但是群成员可以重新加回来。
     *
     * @param groupId 群号
     * @param userId  用户号
     */
    public void kick(long groupId, long userId) {
        send(GroupKickSendParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .rejectAddRequest(false)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 踢出群成员，并且拒绝该成员重新加回来。
     *
     * @param groupId 群号
     * @param userId  用户号
     */
    public void kickReject(long groupId, long userId) {
        send(GroupKickSendParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .rejectAddRequest(true)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }
}
