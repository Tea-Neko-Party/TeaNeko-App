package org.zexnocs.teanekoplugin.general.dice;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.utils.RandomUtil;
import org.zexnocs.teanekoplugin.general.dice.data.DiceAttemptData;
import org.zexnocs.teanekoplugin.general.dice.data.DiceData;
import org.zexnocs.teanekoplugin.general.dice.data.DiceResultData;
import org.zexnocs.teanekoplugin.general.dice.data.DiceSetData;
import org.zexnocs.teanekoplugin.general.dice.event.DiceAfterEvent;
import org.zexnocs.teanekoplugin.general.dice.event.DiceBeforeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骰子服务，提供投骰子相关功能。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Service
public class DiceService {
    /// 最多骰子次数。
    private static final int MAX_DICE_COUNT = 10;

    private final IEventService eventService;
    private final RandomUtil randomUtil;

    @Autowired
    public DiceService(IEventService eventService, RandomUtil randomUtil) {
        this.eventService = eventService;
        this.randomUtil = randomUtil;
    }

    /// scope id -> 骰子数据
    private final Map<String, DiceData> diceDataMap = new ConcurrentHashMap<>();

    /**
     * 获取骰子数据。
     *
     * @param scopeId 范围ID
     * @return 骰子数据
     */
    @NonNull
    public DiceData getDiceData(String scopeId) {
        return diceDataMap.getOrDefault(scopeId,
                new DiceData(scopeId, 1, 100));
    }

    /**
     * 尝试投骰子，推送投骰子前事件。
     *
     * @param messageData   消息数据
     * @param expectedValue 期望最小值
     */
    public void attemptDice(ITeaNekoMessageData messageData, int expectedValue) {
        // 推送投骰子事件
        var attemptDiceData = new DiceAttemptData(messageData, expectedValue, getDiceData(messageData.getScopeId()));
        eventService.pushEvent(new DiceBeforeEvent(attemptDiceData, this));
    }


    /**
     * 去投骰子。
     *
     * @param attemptDiceData 投骰子数据
     */
    public void goDice(DiceAttemptData attemptDiceData) {
        // 推送投骰子事件
        var resultData = DiceResultData.builder()
                .attemptDiceData(attemptDiceData)
                .diceResultList(_generateDiceResult(attemptDiceData.getDiceData()))
                .build();
        // 推送投骰子事件
        eventService.pushEvent(new DiceAfterEvent(resultData));
    }

    /**
     * 设置骰子数据。
     *
     * @param data         消息数据
     */
    public void setDiceData(DiceSetData data) {
        setDiceData(data.getMessageReceiveData(), data.getDiceCount(), data.getDiceMaxValue());
    }

    /**
     * 设置骰子数据。
     *
     * @param data         消息数据
     * @param diceCount    骰子次数
     * @param diceMaxValue 骰子最大值
     */
    public void setDiceData(ITeaNekoMessageData data, int diceCount, int diceMaxValue) {
        diceCount = Math.min(diceCount, MAX_DICE_COUNT);
        var scopeId = data.getScopeId();
        diceDataMap.put(scopeId, new DiceData(scopeId, diceCount, diceMaxValue));
        data.getMessageSender(CommandData.getCommandToken())
                .sendReplyMessage("设置成功，当前投掷次数：" + diceCount + "，最大值：" + diceMaxValue);
    }

    /**
     * 获取骰子数据。
     *
     * @param diceData 骰子数据
     * @return 骰子数据
     */
    private List<Integer> _generateDiceResult(DiceData diceData) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < diceData.getDiceCount(); i++) {
            result.add(randomUtil.nextInt(diceData.getDiceMaxValue()) + 1);
        }
        return result;
    }
}