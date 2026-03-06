package org.zexnocs.teanekoplugin.general.dice.event;

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekoplugin.general.dice.DiceService;
import org.zexnocs.teanekoplugin.general.dice.data.DiceSetData;

/**
 * 设置骰子事件，在设置骰子数据后触发
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
public class DiceSetEvent extends AbstractEvent<DiceSetData> {

    private final DiceService diceService;

    public DiceSetEvent(DiceSetData data, DiceService diceService) {
        super(data);
        this.diceService = diceService;
    }

    @Override
    public void _afterNotify() {
        diceService.setDiceData(getData());
    }
}
