package org.zexnocs.teanekoapp.message.api;

/**
 * Tea Neko 消息接口，所有消息都必须实现此接口。
 * 有多种消息类型，例如文本、图片、at 等。
 *
 * @author zExNocs
 * @date 2026/02/21
 */
public interface ITeaNekoMessage {
    /**
     * 获取消息类型
     * @return 消息类型字符串，例如 "text"、"image"、"at" 等
     */
    String getType();

    /**
     * 获取消息内容
     * @return 消息内容对象，具体类型根据消息类型而定。
     */
    ITeaNekoContent getContent();
}
