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
 * <br>2026/11/06：诊断仅记录安全标识、帧长度与异常定位，不复制消息正文。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 * @version 4.8.4-l
 */
@Event(MetaEvent.KEY)
public class MetaEvent extends AbstractEvent<String> {
    /// 属于 post 事件中的 meta 事件，key 前缀为 post 事件的后缀 + "meta_event"
    public final static String KEY = PostReceiveEvent.SUFFIX_KEY + "meta_event";

    public final static String PARSE_SUFFIX_KEY = "meta_event.";

    private final static Pattern eventTypePattern = Pattern.compile("\"meta_event_type\"\\s*:\\s*\"(.*?)\"");

    /// 共享数据类
    private final OnebotEventShareComponent eventShareComponent;

    /**
     * 借用宿主提供的事件服务。
     * @param information 原协议帧，不进入诊断日志
     * @param eventShareComponent 原共享服务
     */
    public MetaEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(information);
        this.eventShareComponent = eventShareComponent;
    }

    /**
     * 2026/11/06：只报告事件类型、长度及安全根因。
     * @return 下一级事件；未知或构造失败时为空
     */
    @Override
    public @org.jspecify.annotations.Nullable IEvent<?> _getNextEvent() {
        var information = getData();
        Matcher matcher = eventTypePattern.matcher(information);
        if (matcher.find()) {
            String eventType = matcher.group(1);
            String parserKey = PARSE_SUFFIX_KEY + eventType;
            var eventClazz = eventShareComponent.eventScanner.getEventType(parserKey);
            if (eventClazz == null) {
                eventShareComponent.logger.warn(MetaEvent.class.getName(),
                        "未找到 meta_event_type 对应的事件；帧字符数=" + information.length());
                return null;
            }
            // 尝试构造事件
            try {
                return eventClazz.getConstructor(String.class, OnebotEventShareComponent.class)
                        .newInstance(information, eventShareComponent);
            } catch (Exception e) {
                eventShareComponent.logger.errorWithReport(MetaEvent.class.getName(),
                        "创建已注册事件失败；类型=%s；帧字符数=%d"
                                .formatted(eventClazz.getName(), information.length()), e);
                return null;
            }
        } else {
            // 一般不会出现这种情况
            eventShareComponent.logger.errorWithReport(MetaEvent.class.getName(), "未找到 meta_event_type 字段：" + "帧字符数=" + information.length());
            return null;
        }
    }
}
