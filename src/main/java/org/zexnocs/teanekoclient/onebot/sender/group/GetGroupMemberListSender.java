package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupMemberResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GetGroupMemberListParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 获取群 member 所有成员信息的 sender
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Component("GroupMemberListSender")
public class GetGroupMemberListSender extends AbstractOnebotSender<GetGroupMemberListParamsData, GroupMemberResponseData> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public GetGroupMemberListSender(ISenderService senderService,
                                    OnebotClient client,
                                    @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 获取群 member 所有成员信息
     *
     * @param groupId 群组 ID
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link GroupMemberResponseData }>>>
     */
    public TaskFuture<ITaskResult<List<GroupMemberResponseData>>> get(long groupId) {
        return sendWithFuture(
                GetGroupMemberListParamsData
                    .builder()
                    .groupId(groupId)
                    .build(),
                Duration.ZERO,
                3,
                Duration.ofMillis(200));
    }
}
