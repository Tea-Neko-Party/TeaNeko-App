package org.zexnocs.teanekoplugin.general.dice.event;

import org.zexnocs.teanekoplugin.general.dice.DiceService;
import org.zexnocs.teanekoplugin.general.dice.data.DiceAttemptData;

/**
 * 骰子事件，在骰子结果生成前触发
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
public class DiceBeforeEvent extends DiceEvent<DiceAttemptData> {

    private final DiceService diceService;

    /**
     * 关联原始掷骰请求与骰子服务，前置通知后继续同一业务完成链。
     *
     * @param data 本次处理的输入数据
     * @param diceService 由装配方提供的 {@code DiceService} 协作依赖
     */
    public DiceBeforeEvent(DiceAttemptData data, DiceService diceService) {
        super(data);
        this.diceService = diceService;
    }

    /**
     * 在前置事件通知结束后继续既有骰子流程，事件取消和替代反馈仍纳入完成链。
     */
    @Override
    public void _afterNotify() {
        var data = getData();
        includeCompletion(diceService.goDice(data));
    }
}
