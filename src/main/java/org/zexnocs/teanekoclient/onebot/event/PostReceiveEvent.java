package org.zexnocs.teanekoclient.onebot.event;

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * post 事件的一级事件类。
 * <p>所有下级事件类必须提供接收 {@code String, OnebotEventShareComponent} 的构造器。
 *
 * <br>2026/11/06：诊断仅记录安全标识、帧长度与异常定位，不复制消息正文。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 * @version 4.8.4-l
 */
public class PostReceiveEvent extends AbstractEvent<String> {
    /// key 的前缀
    public final static String SUFFIX_KEY = "receive_post.";

    /// 匹配 post_type 的正则表达式
    private final Pattern postTypePattern = Pattern.compile("\"post_type\"\\s*:\\s*\"(.*?)\"");

    /// 共享数据类
    private final OnebotEventShareComponent eventShareComponent;

    /**
     * 借用宿主提供的事件服务。
     * @param information 原协议帧，不进入诊断日志
     * @param eventShareComponent 原共享服务
     */
    public PostReceiveEvent(String information, OnebotEventShareComponent eventShareComponent) {
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
        Matcher postTypeMatcher = postTypePattern.matcher(information);
        if (postTypeMatcher.find()) {
            String postType = postTypeMatcher.group(1);
            var eventClazz = eventShareComponent.eventScanner.getEventType(SUFFIX_KEY + postType);
            if (eventClazz == null) {
                eventShareComponent.logger.warn(this.getClass().getSimpleName(),
                        "未找到 post_type 对应的事件；字符数=" + information.length());
                return null;
            }
            // 尝试构造事件
            try {
                return eventClazz.getConstructor(String.class, OnebotEventShareComponent.class)
                        .newInstance(information, eventShareComponent);
            } catch (Exception e) {
                eventShareComponent.logger.errorWithReport(this.getClass().getSimpleName(),
                        "创建已注册事件失败；类型=%s；帧字符数=%d"
                                .formatted(eventClazz.getName(), information.length()), e);
                return null;
            }
        } else {
            eventShareComponent.logger.errorWithReport(this.getClass().getSimpleName(),
                    "未找到 post_type 字段；帧字符数=" + information.length());
            return null;
        }
    }
}
