package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.tools.IGroupKickSender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GroupKickSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 踢出群成员发送器
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.12
 * @version 4.5.0-l
 */
@SuppressWarnings("rawtypes")
@Component("GroupKickSender")
public class GroupKickSender extends AbstractOnebotSender<GroupKickSendParamsData, Map>
    implements IGroupKickSender {

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

    /**
     * 将用户踢出群组。
     *
     * @param groupId 群组平台 ID
     * @param userId  用户平台 ID
     */
    @Override
    public void kick(String groupId, String userId) {
        kick(Long.parseLong(groupId), Long.parseLong(userId));
    }

    /**
     * 通过原平台发送链移出指定成员，等待实际平台回执，不以任务提交冒充完成。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> kickAsync(long groupId, long userId) {
        return sendAsync(GroupKickSendParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .rejectAddRequest(false)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 按原平台规则移出成员并拒绝再次加群，等待该动作的最终回执。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> kickRejectAsync(long groupId, long userId) {
        return sendAsync(GroupKickSendParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .rejectAddRequest(true)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 通过原平台发送链移出指定成员，等待实际平台回执，不以任务提交冒充完成。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Override
    public CompletionStage<Void> kickAsync(String groupId, String userId) {
        return kickAsync(Long.parseLong(groupId), Long.parseLong(userId));
    }
}
