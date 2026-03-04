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
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.0.14
 */
@Getter
@SuperBuilder
public class OnebotMessageData extends TeaNekoMessageData {
    /// onebot 规范中定义的原始消息数据，包含了所有 onebot 规范中定义的字段和功能
    private final OnebotRawMessageData onebotRawMessageData;
}
