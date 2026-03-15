package org.zexnocs.teanekoclient.onebot.state;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 切换 onebot state 指令。
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
@Description("""
        切换当前 onebot 机器人状态的指令""")
@Command(
        value = {"/state"},
        permission = CommandPermission.DEBUG,
        scope = CommandScope.ALL,
        supportedClients = OnebotTeaNekoClient.class
)
@RequiredArgsConstructor
public class OnebotStateCommand {

    private final OnebotStateMachine onebotStateMachine;

    @Description("""
            切换 onebot 机器人状态的指令。
            当前支持的状态：
            - default: 默认状态
            - stop: 停止所有指令状态
            - debug: 只支持 debugger 指令状态
            - llm: LLM 为主状态""")
    @DefaultCommand
    public void switchState(CommandData<OnebotMessageData> commandData, @DefaultValue("") String state) {
        var data = commandData.getRawData();
        if(state.isBlank()) {
            // 如果为空，则查询当前状态
            data.getMessageSender().sendReplyMessage("当前状态：" +
                    onebotStateMachine.getCurrentState().name());
            return;
        }

        try {
            var switchedState = OnebotState.valueOf(state.toUpperCase());
            onebotStateMachine.switchState(switchedState);
            data.getMessageSender().sendReplyMessage("切换状态 " + switchedState.name() + " 成功喵");
        } catch (IllegalArgumentException e) {
            data.getMessageSender().sendReplyMessage("无效的状态喵。");
        }
    }
}
