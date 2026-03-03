package org.zexnocs.teanekoclient.onebot.event;

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * post 事件的一级事件类。
 * <p>所有子集事件类的构造器必须是 {@code String, OnebotEventShareData} 的形式。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
public class PostReceiveEvent extends AbstractEvent<String> {
    /// key 的前缀
    public final static String SUFFIX_KEY = "receive_post.";

    /// 匹配 post_type 的正则表达式
    private final Pattern postTypePattern = Pattern.compile("\"post_type\"\\s*:\\s*\"(.*?)\"");

    /// 共享数据类
    private final OnebotEventShareComponent eventShareComponent;

    /// 构造器
    public PostReceiveEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(information);
        this.eventShareComponent = eventShareComponent;
    }

    @Override
    public IEvent<?> _getNextEvent() {
        var information = getData();
        Matcher postTypeMatcher = postTypePattern.matcher(information);
        if (postTypeMatcher.find()) {
            String postType = postTypeMatcher.group(1);
            var eventClazz = eventShareComponent.eventScanner.getEventType(SUFFIX_KEY + postType);
            if (eventClazz == null) {
                eventShareComponent.logger.warn(this.getClass().getSimpleName(),
                        "未找到 post_type 对应的事件: %s\n信息: %s".formatted(postType, information));
                return null;
            }
            // 尝试构造事件
            try {
                return eventClazz.getConstructor(String.class, OnebotEventShareComponent.class)
                        .newInstance(information, eventShareComponent);
            } catch (Exception e) {
                eventShareComponent.logger.errorWithReport(this.getClass().getSimpleName(),
                        "构造事件失败，该事件类的构造函数不接受 String, OnebotEventShareComponent 类型的参数: %s\n信息: %s"
                                .formatted(eventClazz.getName(), information), e);
                return null;
            }
        } else {
            eventShareComponent.logger.errorWithReport(this.getClass().getSimpleName(),
                    "未找到 post_type 字段: %s".formatted(information));
            return null;
        }
    }
}
