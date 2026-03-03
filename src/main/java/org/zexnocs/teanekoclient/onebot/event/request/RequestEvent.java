package org.zexnocs.teanekoclient.onebot.event.request;

import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekoclient.onebot.event.PostReceiveEvent;
import org.zexnocs.teanekoclient.onebot.event.meta.MetaEvent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * post 事件的 request 二级事件类。
 * <p>所有下级事件都必须有 {@code String, OnebotEventShareData} 的构造器。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Event(RequestEvent.KEY)
public class RequestEvent extends AbstractEvent<String> {
    public static final String KEY = PostReceiveEvent.SUFFIX_KEY + "request";

    public final static String PARSE_SUFFIX_KEY = "request.";

    private final static Pattern requestTypePattern = Pattern.compile("\"request_type\"\\s*:\\s*\"(.*?)\"");

    /// 共享数据类
    private final OnebotEventShareComponent eventShareComponent;

    public RequestEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(information);
        this.eventShareComponent = eventShareComponent;
    }

    /**
     * 根据 request_type 字段的值，找到对应的事件类，并尝试构造事件实例。
     *
     * @return {@link IEvent }<{@link ? }>
     */
    @Override
    public IEvent<?> _getNextEvent() {
        var information = getData();
        Matcher matcher = requestTypePattern.matcher(information);
        if (matcher.find()) {
            String requestType = matcher.group(1);
            String parserKey = PARSE_SUFFIX_KEY + requestType;
            var eventClazz = eventShareComponent.eventScanner.getEventType(parserKey);
            if (eventClazz == null) {
                eventShareComponent.logger.errorWithReport(RequestEvent.class.getName(),
                        "未找到 request_type 对应的事件: %s\n信息: %s".formatted(requestType, information));
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
            eventShareComponent.logger.errorWithReport(RequestEvent.class.getName(), "未找到 request_type 字段：" + information);
            return null;
        }
    }
}
