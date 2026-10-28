package org.zexnocs.teanekoplugin.general.dice.event;

import org.zexnocs.teanekoplugin.general.dice.DiceService;
import org.zexnocs.teanekoplugin.general.dice.data.DiceSetData;

/**
 * 设置骰子事件，在设置骰子数据后触发
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
public class DiceSetEvent extends DiceEvent<DiceSetData> {

    private final DiceService diceService;

    /**
     * 关联待设置的骰子数据与业务服务，设置允许后才进入状态变更及反馈链。
     *
     * @param data 本次处理的输入数据
     * @param diceService 由装配方提供的 {@code DiceService} 协作依赖
     */
    public DiceSetEvent(DiceSetData data, DiceService diceService) {
        super(data);
        this.diceService = diceService;
    }

    /**
     * 在设置事件允许后提交骰子状态并等待反馈，取消时保留既有状态。
     */
    @Override
    public void _afterNotify() {
        includeCompletion(diceService.setDiceData(getData()));
    }
}
