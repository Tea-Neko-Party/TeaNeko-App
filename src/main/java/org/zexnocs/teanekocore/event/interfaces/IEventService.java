package org.zexnocs.teanekocore.event.interfaces;

/**
 * 事件服务接口。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public interface IEventService {
    /**
     * 推送事件
     * @param event 事件
     */
    void pushEvent(IEvent<?> event);
}
