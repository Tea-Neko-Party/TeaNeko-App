package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GroupBanParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

/**
 * 群禁言发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@SuppressWarnings("rawtypes")
@Component("GroupBanSender")
public class GroupBanSender extends AbstractOnebotSender<GroupBanParamsData, Map> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public GroupBanSender(ISenderService senderService,
                          OnebotClient client,
                          @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 禁言群成员
     *
     * @param groupId           群号
     * @param userId            成员QQ号
     * @param durationInSeconds 禁言时长（秒）
     */
    public void ban(long groupId, long userId, long durationInSeconds) {
        send(GroupBanParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .duration(durationInSeconds)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 取消禁言群成员
     *
     * @param groupId 群号
     * @param userId  成员QQ号
     */
    public void unban(long groupId, long userId) {
        send(GroupBanParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .duration(0)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }
}
