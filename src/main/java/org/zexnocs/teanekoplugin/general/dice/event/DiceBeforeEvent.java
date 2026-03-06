package org.zexnocs.teanekoplugin.general.dice.event;

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekoplugin.general.dice.DiceService;
import org.zexnocs.teanekoplugin.general.dice.data.DiceAttemptData;

/**
 * 骰子事件，在骰子结果生成前触发
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
public class DiceBeforeEvent extends AbstractEvent<DiceAttemptData> {

    private final DiceService diceService;

    public DiceBeforeEvent(DiceAttemptData data, DiceService diceService) {
        super(data);
        this.diceService = diceService;
    }

    @Override
    public void _afterNotify() {
        var data = getData();
        diceService.goDice(data);
    }
}
