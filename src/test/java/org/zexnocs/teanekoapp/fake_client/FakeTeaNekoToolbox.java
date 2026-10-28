package org.zexnocs.teanekoapp.fake_client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.tools.IAvatarGetter;
import org.zexnocs.teanekoapp.client.tools.IGroupKickSender;
import org.zexnocs.teanekoapp.client.tools.IPlatformUserInfoConstructor;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.fake_client.message.FakeMessageSenderTools;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberInfoSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberListSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserGetSender;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * fake tea neko client 工具箱
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.2.3
 * @version 4.5.0-l
 */
@Component
@RequiredArgsConstructor
public class FakeTeaNekoToolbox implements ITeaNekoToolbox {
    /// 日志记录器
    @Getter
    private final ILogger logger;

    /// 获取消息发送器工具
    @Getter
    private final FakeMessageSenderTools messageSenderTools;

    private final FakeClient client;

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @return 按已知消息 ID 查询测试输入的发送适配器
     */
    @Override
    public IGetMessageSender getGetMsgSender() {
        return messageId -> FakeTaskFutures.completed(
                logger,
                "fake get message " + messageId,
                new TaskResult<>(client.getReceivedMessage(messageId) != null, client.getReceivedMessage(messageId))
        );
    }

    /**
     * 获取平台用户在指定群组中的信息的发送器。
     *
     * @return 获取平台用户在指定群组中的信息的发送器
     */
    @Override
    public IGetGroupMemberInfoSender getGroupMemberInfoSender() {
        return (groupId, userId) -> FakeTaskFutures.completed(
                logger,
                "fake get group member " + groupId + ":" + userId,
                client.getOrCreateGroupMember(groupId, userId)
        );
    }

    /**
     * 获取指定群组中所有成员信息的发送器。
     *
     * @return 获取指定群组中所有成员信息的发送器。
     */
    @Override
    public IGetGroupMemberListSender getGroupMemberListSender() {
        return groupId -> FakeTaskFutures.completed(
                logger,
                "fake get group member list " + groupId,
                client.getGroupMembers(groupId)
        );
    }

    /**
     * 获取平台用户信息的发送器
     *
     * @return 获取平台用户信息的发送器
     */
    @Override
    public IPlatformUserGetSender getPlatformUserGetSender() {
        return userId -> FakeTaskFutures.completed(
                logger,
                "fake get platform user " + userId,
                client.getOrCreatePlatformUser(userId)
        );
    }

    /**
     * 获取构造平台用户信息的发送器。
     * 该发射器可以根据平台 ID 构造出用户的信息列表。
     *
     * @return 满足本方法约束的 {@code IPlatformUserInfoConstructor} 结果
     */
    @Override
    public IPlatformUserInfoConstructor getPlatformUserInfoConstructorSender() {
        return platformId -> DefaultTeaNekoContentListBuilder.builder()
                .addText("User: " + client.getOrCreatePlatformUser(platformId).getNickname())
                .addText("\nID: " + platformId)
                .build();
    }

    /**
     * 获取可以根据用户平台 ID 获取头像 url 的工具
     *
     * @return 头像 url 工具
     */
    @Override
    public IAvatarGetter getAvatarGetter() {
        return client::getAvatarUrl;
    }

    /**
     * 群组成员踢出器
     *
     * @return 记录模拟群成员移出动作的发送适配器
     */
    @Override
    public IGroupKickSender getGroupKickSender() {
        return new IGroupKickSender() {
            /**
             * 记录受控踢出动作，不访问真实平台。
             *
             * @param groupId 目标群组标识
             * @param userId 原业务边界中的目标用户标识
             */
            @Override
            public void kick(String groupId, String userId) {
                client.recordKick(groupId, userId);
            }

            /**
             * 记录受控踢出动作并返回明确完成阶段，供平台动作契约验证。
             *
             * @param groupId 目标群组标识
             * @param userId 原业务边界中的目标用户标识
             * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
             */
            @Override
            public java.util.concurrent.CompletionStage<Void> kickAsync(String groupId, String userId) {
                client.recordKick(groupId, userId);
                return java.util.concurrent.CompletableFuture.completedStage(null);
            }
        };
    }
}
