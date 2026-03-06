package org.zexnocs.teanekoplugin.general.dice;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekoplugin.general.dice.data.DiceSetData;
import org.zexnocs.teanekoplugin.general.dice.event.DiceSetEvent;

/**
 * 骰子命令。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
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

    @Autowired
    public DiceCommand(DiceService diceService,
                       IEventService eventService) {
        this.diceService = diceService;
        this.eventService = eventService;
    }

    @Description("投一个骰子！规格：/dice <期望值[可选]>")
    @DefaultCommand
    public void dice(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("-1") int expectedValue) {
        // 如果消息数据为空，直接返回
        var messageData = commandData.getRawData();
        diceService.attemptDice(messageData, expectedValue);
    }

    @Description("设置骰子数量和最大值，规范：/dice set [骰子数量]d[骰子最大值]")
    @SubCommand("set")
    public void set(CommandData<ITeaNekoMessageData> commandData, String dice) {
        var messageData = commandData.getRawData();

        // 尝试解析骰子数量和最大值
        String[] diceArray = dice.split("d");
        diceArray = diceArray.length == 2 ? diceArray : dice.split("D");

        // 如果解析失败，发送错误消息
        if (diceArray.length != 2) {
            messageData.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage(ERROR_MESSAGE);
            return;
        }

        try {
            // 解析骰子数量和最大值为整型
            int diceCount = Integer.parseInt(diceArray[0]);
            int diceMaxValue = Integer.parseInt(diceArray[1]);
            // 如果不为正整数，发送错误消息
            if (diceCount <= 0 || diceMaxValue <= 0) {
                messageData.getMessageSender(CommandData.getCommandToken())
                        .sendReplyMessage(ERROR_MESSAGE);
                return;
            }
            eventService.pushEvent(new DiceSetEvent(new DiceSetData(messageData, diceCount, diceMaxValue),
                                    diceService));
        } catch (NumberFormatException e) {
            // 解析失败，发送错误消息
            messageData.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage(ERROR_MESSAGE);
        }
    }
}
