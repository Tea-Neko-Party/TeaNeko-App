package org.zexnocs.teanekoplugin.general.dice;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骰子服务，提供投骰子相关功能。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Service
public class DiceService {
    /// 最多骰子次数。
    private static final int MAX_DICE_COUNT = 10;

    private final IEventService eventService;
    private final RandomUtil randomUtil;

    /**
     * 装配原业务事件与随机数服务，骰子状态和通知通过现有事件链推进。
     *
     * @param eventService 由装配方提供的 {@code IEventService} 协作依赖
     * @param randomUtil 用于本次处理的 {@code randomUtil} 数据
     */
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
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> attemptDice(ITeaNekoMessageData messageData, int expectedValue) {
        // 推送投骰子事件
        var attemptDiceData = new DiceAttemptData(messageData, expectedValue, getDiceData(messageData.getScopeId()));
        var event = new DiceBeforeEvent(attemptDiceData, this);
        return TaskStages.successful(eventService.pushEventWithFuture(event), "投骰子前事件")
                .thenCompose(ignored -> event.getCompletion());
    }


    /**
     * 去投骰子。
     *
     * @param attemptDiceData 投骰子数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> goDice(DiceAttemptData attemptDiceData) {
        // 推送投骰子事件
        var resultData = DiceResultData.builder()
                .attemptDiceData(attemptDiceData)
                .diceResultList(_generateDiceResult(attemptDiceData.getDiceData()))
                .build();
        // 推送投骰子事件
        var event = new DiceAfterEvent(resultData);
        return TaskStages.successful(eventService.pushEventWithFuture(event), "投骰子后事件")
                .thenCompose(ignored -> event.getCompletion());
    }

    /**
     * 设置骰子数据。
     *
     * @param data         消息数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> setDiceData(DiceSetData data) {
        return setDiceData(data.getMessageReceiveData(), data.getDiceCount(), data.getDiceMaxValue());
    }

    /**
     * 设置骰子数据。
     *
     * @param data         消息数据
     * @param diceCount    骰子次数
     * @param diceMaxValue 骰子最大值
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> setDiceData(ITeaNekoMessageData data, int diceCount, int diceMaxValue) {
        diceCount = Math.min(diceCount, MAX_DICE_COUNT);
        var scopeId = data.getScopeId();
        diceDataMap.put(scopeId, new DiceData(scopeId, diceCount, diceMaxValue));
        return data.getMessageSender()
                .sendReplyMessageAsync("设置成功，当前投掷次数：" + diceCount + "，最大值：" + diceMaxValue);
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
