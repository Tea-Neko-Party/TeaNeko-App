package org.zexnocs.teanekocore.event.object;

import org.zexnocs.teanekocore.event.AbstractEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个示例事件类，用于测试事件系统的功能。
 * 是 BEvent 的父类。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public class AEvent extends AbstractEvent<AtomicInteger>  {
    /**
     * @param integer 用于是否触发了事件的标志。当事件被触发时，次数会增加 1。
     */
    public AEvent(AtomicInteger integer) {
        super(integer, AtomicInteger.class);
    }
}
