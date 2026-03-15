package org.zexnocs.teanekoclient.onebot.state.manager.instance;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekoclient.onebot.state.OnebotState;
import org.zexnocs.teanekoclient.onebot.state.OnebotStateCommand;
import org.zexnocs.teanekoclient.onebot.state.manager.interfaces.IOnebotStateManager;
import org.zexnocs.teanekoclient.onebot.state.manager.interfaces.OnebotStateManager;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.event.CommandDispatchEvent;
import org.zexnocs.teanekocore.command.event.CommandExecuteEvent;

/**
 * 停止所有的交互。
 *
 * @see OnebotState#STOP
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
@OnebotStateManager(OnebotState.STOP)
@RequiredArgsConstructor
public class StopOnebotStateManager implements IOnebotStateManager {
    private final ITaskService iTaskService;

    /**
     * 处理 command dispatch event
     *
     * @param event 指令派送事件
     */
    @Override
    public void processCommandDispatchEvent(CommandDispatchEvent event) {
        // 取消所有的处理器
        event.setErrorHandler(null);
        event.setHelpSubCommandHandler(null);
    }

    /**
     * 处理 command execute event
     *
     * @param event 指令派送事件
     */
    @Override
    public void processCommandExecuteEvent(CommandExecuteEvent event) {
        // 如果是 set state 事件，则跳过
        var object = event.getMapData().getCommand();
        if(object instanceof OnebotStateCommand) {
            return;
        }
        // 否则取消
        event.setCancelled(true);
    }

    /**
     * 处理 sender event
     *
     * @param event sender event
     */
    @Override
    public void processMessageSentEvent(OnebotMessageSentEvent<?> event) {
        // 取消
        event.safeSetCancelled(iTaskService);
    }
}
