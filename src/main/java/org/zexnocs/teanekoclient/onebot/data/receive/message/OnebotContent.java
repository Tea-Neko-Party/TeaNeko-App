package org.zexnocs.teanekoclient.onebot.data.receive.message;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoContent;
import tools.jackson.databind.ObjectMapper;

/**
 * Onebot消息类，继承自TeaNekoMessage，但可以用 OnebotMessageDeserializer 进行反序列化。
 * <p>需要使用 spring 自带的 {@link ObjectMapper} 来反序列化 {@link OnebotContent} 对象，以确保 {@link OnebotMessageDeserializer} 能够正确工作。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class OnebotContent extends TeaNekoContent {
    /**
     * onebot 消息内容前缀，用于注册消息内容类。
     */
    public static final String PREFIX = "Onebot-";
}
