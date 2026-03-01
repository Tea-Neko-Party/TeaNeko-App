package org.zexnocs.teanekoclient.onebot.data.receive.message;

import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoMessage;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Onebot消息类，继承自TeaNekoMessage，但可以用 OnebotMessageDeserializer 进行反序列化。
 * <p>需要使用 spring 自带的 {@link ObjectMapper} 来反序列化 {@link OnebotMessage} 对象，以确保 {@link OnebotMessageDeserializer} 能够正确工作。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@SuperBuilder
@JsonDeserialize(using = OnebotMessageDeserializer.class)
public class OnebotMessage extends TeaNekoMessage {
    /**
     * onebot 消息内容前缀，用于注册消息内容类。
     */
    public static final String PREFIX = "Onebot-";
}
