package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberInfoSender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupMemberResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GetGroupMemberInfoParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 获取群成员信息发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Component("Onebot-GroupMemberInfoSender")
public class GetGroupMemberInfoSender extends AbstractOnebotSender<GetGroupMemberInfoParamsData,
        GroupMemberResponseData> implements IGetGroupMemberInfoSender {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public GetGroupMemberInfoSender(ISenderService senderService,
                                    OnebotClient client,
                                    @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 获取群成员信息。
     *
     * @param groupId 群号
     * @param userId  成员QQ号
     */
    public TaskFuture<GroupMemberResponseData> get(String groupId, String userId) {
        return sendWithFuture(
                GetGroupMemberInfoParamsData
                    .builder()
                    .groupId(Long.parseLong(groupId))
                    .userId(Long.parseLong(userId))
                    .noCache(true)
                    .build(),
                Duration.ZERO,
                3,
                Duration.ofMillis(200))
                .thenApply(r -> {
                    if(!r.isSuccess()) {
                        return null;
                    }
                    return r.getResult().getFirst();
                });
    }
}
