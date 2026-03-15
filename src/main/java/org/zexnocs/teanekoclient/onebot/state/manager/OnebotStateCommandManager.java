package org.zexnocs.teanekoclient.onebot.state.manager;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.state.OnebotStateMachine;
import org.zexnocs.teanekocore.command.event.CommandDispatchEvent;
import org.zexnocs.teanekocore.command.event.CommandExecuteEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 根据 onebot 处理 command 相关事件。
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
@EventListener
@RequiredArgsConstructor
public class OnebotStateCommandManager {
    private final OnebotStateMachine onebotStateMachine;
    private final OnebotStateManagerScanner onebotStateManagerScanner;

    /**
     * 派送事件
     *
     * @param event command dispatch 事件
     */
    @EventHandler(priority = 1000)
    public void handle(CommandDispatchEvent event) {
        // 判断是不是 onebot 的 command 事件
        var data = event.getData();
        if(!data.getClientClass().equals(OnebotTeaNekoClient.class)) {
            return;
        }

        // 获取当前的状态
        var state = onebotStateMachine.getCurrentState();
        var manager = onebotStateManagerScanner.get(state);
        if(manager != null) {
            manager.processCommandDispatchEvent(event);
        }
    }


    /**
     * 执行事件
     *
     * @param event command execute 事件
     */
    @EventHandler(priority = 1000)
    public void handle(CommandExecuteEvent event) {
        // 判断是不是 onebot 的 command 事件
        var data = event.getData();
        if(!data.getClientClass().equals(OnebotTeaNekoClient.class)) {
            return;
        }

        // 获取当前的状态
        var state = onebotStateMachine.getCurrentState();
        var manager = onebotStateManagerScanner.get(state);
        if(manager != null) {
            manager.processCommandExecuteEvent(event);
        }
    }
}
