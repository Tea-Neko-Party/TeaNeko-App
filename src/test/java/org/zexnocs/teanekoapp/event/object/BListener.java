package org.zexnocs.teanekoapp.event.object;

import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 一个示例事件监听器，用于测试事件系统的功能。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@EventListener
public class BListener {
    @EventHandler
    public void onBEvent(BEvent event) {
        System.out.printf("BListener triggered: %s, data: %d%n",
                event.getClass().getSimpleName(), event.getData().incrementAndGet());
    }
}
