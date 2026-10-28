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
import java.util.concurrent.CompletionStage;

/**
 * 群禁言发送器
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.12
 * @version 4.5.0-l
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

    /**
     * 提交指定成员的禁言操作，沿用原发送权限和状态约束并等待平台回执。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @param durationInSeconds 禁言或动作持续秒数
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> banAsync(long groupId, long userId, long durationInSeconds) {
        return sendAsync(GroupBanParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .duration(durationInSeconds)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 按平台协议解除指定成员禁言，最终失败或缺失回执以异常传播。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> unbanAsync(long groupId, long userId) {
        return sendAsync(GroupBanParamsData.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .duration(0)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }
}
