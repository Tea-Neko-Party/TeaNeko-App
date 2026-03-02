package org.zexnocs.teanekoclient.onebot.event.meta;

import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekoclient.onebot.event.PostReceiveEvent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * post 事件的 meta 二级事件类。
 * <p>所有下级 meta 事件都必须有 {@code String, OnebotEventShareComponent } 的构造器。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Event(MetaEvent.KEY)
public class MetaEvent extends AbstractEvent<String> {
    /// 属于 post 事件中的 meta 事件，key 前缀为 post 事件的后缀 + "meta_event"
    public final static String KEY = PostReceiveEvent.SUFFIX_KEY + "meta_event";

    public final static String PARSE_SUFFIX_KEY = "meta_event.";

    private final static Pattern eventTypePattern = Pattern.compile("\"meta_event_type\"\\s*:\\s*\"(.*?)\"");

    /// 共享数据类
    private final OnebotEventShareComponent eventShareComponent;

    public MetaEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(information, String.class);
        this.eventShareComponent = eventShareComponent;
    }

    @Override
    public IEvent<?> _getNextEvent() {
        var information = getData();
        Matcher matcher = eventTypePattern.matcher(information);
        if (matcher.find()) {
            String eventType = matcher.group(1);
            String parserKey = PARSE_SUFFIX_KEY + eventType;
            var eventClazz = eventShareComponent.eventScanner.getEventType(parserKey);
            if (eventClazz == null) {
                eventShareComponent.logger.warn(MetaEvent.class.getName(),
                        "未找到 meta_event_type 对应的事件: %s\n信息: %s"
                                .formatted(eventType, information));
                return null;
            }
            // 尝试构造事件
            try {
                return eventClazz.getConstructor(String.class, OnebotEventShareComponent.class)
                        .newInstance(information, eventShareComponent);
            } catch (Exception e) {
                eventShareComponent.logger.errorWithReport(MetaEvent.class.getName(),
                        "构造事件失败，该事件类的构造函数不接受 String, OnebotEventShareComponent 类型的参数: %s\n信息: %s"
                                .formatted(eventClazz.getName(), information), e);
                return null;
            }
        } else {
            // 一般不会出现这种情况
            eventShareComponent.logger.errorWithReport(MetaEvent.class.getName(), "未找到 meta_event_type 字段：" + information);
            return null;
        }
    }
}
