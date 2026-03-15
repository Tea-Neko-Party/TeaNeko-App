package org.zexnocs.teanekoclient.onebot.state.manager.interfaces;

import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekocore.command.event.CommandDispatchEvent;
import org.zexnocs.teanekocore.command.event.CommandExecuteEvent;

/**
 * 对于每个状态处理每一种情况的处理器。
 * <br>需要加上 {@link OnebotStateManager} 注解。
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
public interface IOnebotStateManager {
    /**
     * 处理 command dispatch event
     *
     * @param event 指令派送事件
     */
    void processCommandDispatchEvent(CommandDispatchEvent event);

    /**
     * 处理 command execute event
     *
     * @param event 指令派送事件
     */
    void processCommandExecuteEvent(CommandExecuteEvent event);


    /**
     * 处理 sender event
     *
     * @param event sender event
     */
    void processMessageSentEvent(OnebotMessageSentEvent<?> event);
}
