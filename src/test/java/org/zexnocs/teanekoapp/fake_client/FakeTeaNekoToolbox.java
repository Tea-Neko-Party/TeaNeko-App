package org.zexnocs.teanekoapp.fake_client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.fake_client.message.FakeMessageSenderTools;
import org.zexnocs.teanekoapp.sender.api.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberInfoSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserGetSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserInfoConstructor;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * fake tea neko client 工具箱
 *
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.2.3
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

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @return {@link IGetMessageSender }
     */
    @Override
    public IGetMessageSender getGetMsgSender() {
        return null;
    }

    /**
     * 获取平台用户在指定群组中的信息的发送器。
     *
     * @return 获取平台用户在指定群组中的信息的发送器
     */
    @Override
    public IGetGroupMemberInfoSender getGroupInfoGetSender() {
        return null;
    }

    /**
     * 获取平台用户信息的发送器
     *
     * @return 获取平台用户信息的发送器
     */
    @Override
    public IPlatformUserGetSender getPlatformUserGetSender() {
        return null;
    }

    /**
     * 获取构造平台用户信息的发送器。
     * 该发射器可以根据平台 ID 构造出用户的信息列表。
     */
    @Override
    public IPlatformUserInfoConstructor getPlatformUserInfoConstructorSender() {
        return null;
    }
}
