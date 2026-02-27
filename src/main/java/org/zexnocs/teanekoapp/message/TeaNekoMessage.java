package org.zexnocs.teanekoapp.message;

import com.fasterxml.jackson.annotation.JsonProperty;
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
     * 消息内容前缀，用于注册消息内容类。
     */
    public static final String PREFIX = "TeaNeko-";

    /**
     * 消息类型，例如 "text"、"image"、"at" 等。
     */
    @Getter
    @JsonProperty("type")
    private final String type;

    /**
     * 消息内容对象，具体类型根据消息类型而定。必须实现 ITeaNekoContent 接口。
     */
    @Getter
    @JsonProperty("data")
    private final ITeaNekoContent content;
}
