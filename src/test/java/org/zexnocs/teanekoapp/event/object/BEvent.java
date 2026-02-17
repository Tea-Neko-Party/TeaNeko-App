package org.zexnocs.teanekoapp.event.object;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个示例事件类，用于测试事件系统的功能。
 * 是 AEvent 的子类。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public class BEvent extends AEvent {
    /**
     * @param integer 用于是否触发了事件的标志。当事件被触发时，次数会增加 1。
     */
    public BEvent(AtomicInteger integer) {
        super(integer);
    }
}
