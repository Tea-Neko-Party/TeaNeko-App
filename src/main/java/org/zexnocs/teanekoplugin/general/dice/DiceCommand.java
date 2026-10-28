package org.zexnocs.teanekoplugin.general.dice;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekoplugin.general.dice.data.DiceSetData;
import org.zexnocs.teanekoplugin.general.dice.event.DiceSetEvent;

import java.util.concurrent.CompletionStage;

/**
 * 骰子命令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("投一个骰子！规格：/dice <期望值[可选]>")
@Command(value = {"/dice", "骰子", "/d", "d"},
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL)
public class DiceCommand {
    private static final String ERROR_MESSAGE = """
                骰子设置格式错误喵。
                请使用 /dice set [骰子数量]d[骰子最大值] 格式喵。
                骰子数量和最大值必须是正整数喵。
                骰子数量最多为 10 喵，超过会被自动设置为 10 喵。""";

    private final DiceService diceService;
    private final IEventService eventService;

    /**
     * 组合骰子业务与事件入口，使默认或替代通知均纳入命令完成链。
     *
     * @param diceService 由装配方提供的 {@code DiceService} 协作依赖
     * @param eventService 由装配方提供的 {@code IEventService} 协作依赖
     */
    @Autowired
    public DiceCommand(DiceService diceService,
                       IEventService eventService) {
        this.diceService = diceService;
        this.eventService = eventService;
    }

    /**
     * 投一个骰子！规格：/dice &lt;期望值[可选]&gt;；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param expectedValue 用于本次处理的 {@code expectedValue} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("投一个骰子！规格：/dice <期望值[可选]>")
    @DefaultCommand
    public CompletionStage<Void> dice(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("-1") int expectedValue) {
        // 如果消息数据为空，直接返回
        var messageData = commandData.getRawData();
        return diceService.attemptDice(messageData, expectedValue);
    }

    /**
     * 设置骰子数量和最大值，规范：/dice set [骰子数量]d[骰子最大值]；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param dice 用于本次处理的 {@code dice} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("设置骰子数量和最大值，规范：/dice set [骰子数量]d[骰子最大值]")
    @SubCommand("set")
    public CompletionStage<Void> set(CommandData<ITeaNekoMessageData> commandData, String dice) {
        var messageData = commandData.getRawData();

        // 尝试解析骰子数量和最大值
        String[] diceArray = dice.split("d");
        diceArray = diceArray.length == 2 ? diceArray : dice.split("D");

        // 如果解析失败，发送错误消息
        if (diceArray.length != 2) {
            return messageData.getMessageSender()
                    .sendReplyMessageAsync(ERROR_MESSAGE);
        }

        try {
            // 解析骰子数量和最大值为整型
            int diceCount = Integer.parseInt(diceArray[0]);
            int diceMaxValue = Integer.parseInt(diceArray[1]);
            // 如果不为正整数，发送错误消息
            if (diceCount <= 0 || diceMaxValue <= 0) {
                return messageData.getMessageSender()
                        .sendReplyMessageAsync(ERROR_MESSAGE);
            }
            var event = new DiceSetEvent(new DiceSetData(messageData, diceCount, diceMaxValue), diceService);
            return TaskStages.successful(eventService.pushEventWithFuture(event), "设置骰子事件")
                    .thenCompose(ignored -> event.getCompletion());
        } catch (NumberFormatException e) {
            // 解析失败，发送错误消息
            return messageData.getMessageSender()
                    .sendReplyMessageAsync(ERROR_MESSAGE);
        }
    }
}
