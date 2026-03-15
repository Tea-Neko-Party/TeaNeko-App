package org.zexnocs.teanekoclient.onebot.state;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.framework.state.LockStateMachine;

/**
 * onebot 机器人状态机
 *
 * @see OnebotState
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.3.2
 */
@Component
public class OnebotStateMachine extends LockStateMachine<OnebotState> {
    /**
     * 构造函数，规定初始状态
     *
     */
    public OnebotStateMachine() {
        super(OnebotState.DEFAULT);
    }
}
