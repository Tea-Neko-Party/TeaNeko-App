package org.zexnocs.teanekoapp.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;

/**
 * Tea Neko 消息实现类。
 *
 * @see ITeaNekoMessage
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.9
 */
@Builder
@AllArgsConstructor
public class TeaNekoMessage implements ITeaNekoMessage {

    /**
     * 消息类型，例如 "text"、"image"、"at" 等。
     */
    @Getter
    private final String type;

    /**
     * 消息内容对象，具体类型根据消息类型而定。必须实现 ITeaNekoContent 接口。
     */
    @Getter
    private final ITeaNekoContent content;
}
