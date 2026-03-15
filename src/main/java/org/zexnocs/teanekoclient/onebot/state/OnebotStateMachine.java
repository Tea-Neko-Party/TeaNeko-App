package org.zexnocs.teanekoclient.onebot.state;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoclient.onebot.config.OnebotMainFileConfig;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
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
    private final IFileConfigService iFileConfigService;

    /**
     * 构造函数，规定初始状态
     *
     */
    @Autowired
    public OnebotStateMachine(IFileConfigService iFileConfigService) {
        super(OnebotState.STOP);
        this.iFileConfigService = iFileConfigService;
    }

    /// 结束后使用
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        this.switchState(iFileConfigService.get(OnebotMainFileConfig.class)
                .getState().getInitialState());
    }
}
