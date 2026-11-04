package org.zexnocs.teanekoclient.onebot.data.receive.message;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoMessageData;

/**
 * 可以支持
 * {@link org.zexnocs.teanekocore.command.CommandData}
 * 直接监听的
 * {@link TeaNekoMessageData}
 * 类
 * <br>2026/11/02：通过通用消息接口保留原生群名片。
 * <br>2026/11/04：将原始群名称提供给聊天目录和模型消息上下文。
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.0.14
 * @version 4.7.4-l
 */
@Getter
@SuperBuilder
public class OnebotMessageData extends TeaNekoMessageData {
    /// onebot 规范中定义的原始消息数据，包含了所有 onebot 规范中定义的字段和功能
    private final OnebotRawMessageData onebotRawMessageData;

    /**
     * 2026/11/04：直接保留平台消息携带的群名称，不用群号或发言人昵称代替群名。
     *
     * @return 平台已知群名称，未提供时为空
     */
    @Override
    public String getScopeName() {
        String name = onebotRawMessageData == null ? null : onebotRawMessageData.getGroupName();
        return name == null ? "" : name;
    }

    /**
     * 2026/11/02：保留消息接收时的群名片，不用可修改的显示名替代稳定身份。
     * @return 平台提供的名片，未提供时为空字符串
     */
    @Override public String getSenderGroupNickname() {
        var raw = onebotRawMessageData;
        return raw == null || raw.getSender() == null || raw.getSender().getCard() == null ? "" : raw.getSender().getCard();
    }
}
