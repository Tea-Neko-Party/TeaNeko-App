package org.zexnocs.teanekoclient.onebot.state.manager;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekoclient.onebot.state.OnebotStateMachine;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 处理 message sender 的管理器
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
@EventListener
@RequiredArgsConstructor
public class OnebotStateMessageSenderManager {
    private final OnebotStateMachine onebotStateMachine;
    private final OnebotStateManagerScanner onebotStateManagerScanner;

    /**
     * 处理事件
     *
     * @param event 事件
     */
    @EventHandler(priority = 1000)
    public void handle(OnebotMessageSentEvent<?> event) {
        // 获取当前的状态
        var state = onebotStateMachine.getCurrentState();
        var manager = onebotStateManagerScanner.get(state);
        if(manager != null) {
            manager.processMessageSentEvent(event);
        }
    }
}
