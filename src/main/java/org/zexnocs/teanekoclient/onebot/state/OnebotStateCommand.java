package org.zexnocs.teanekoclient.onebot.state;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 切换 onebot state 指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.3.3
 * @version 4.5.0-l
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

    /**
     * 切换 onebot 机器人状态的指令；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param state 当前管线执行状态
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            切换 onebot 机器人状态的指令。
            当前支持的状态：
            - default: 默认状态
            - stop: 停止所有指令状态
            - debug: 只支持 debugger 指令状态
            - llm: LLM 为主状态""")
    @DefaultCommand
    public CompletionStage<Void> switchState(CommandData<OnebotMessageData> commandData, @DefaultValue("") String state) {
        var data = commandData.getRawData();
        if(state.isBlank()) {
            // 如果为空，则查询当前状态
            return data.getMessageSender().sendReplyMessageAsync("当前状态：" +
                    onebotStateMachine.getCurrentState().name());
        }

        try {
            var switchedState = OnebotState.valueOf(state.toUpperCase());
            onebotStateMachine.switchState(switchedState);
            return data.getMessageSender().sendReplyMessageAsync("切换状态 " + switchedState.name() + " 成功喵");
        } catch (IllegalArgumentException e) {
            return data.getMessageSender().sendReplyMessageAsync("无效的状态喵。");
        }
    }
}
