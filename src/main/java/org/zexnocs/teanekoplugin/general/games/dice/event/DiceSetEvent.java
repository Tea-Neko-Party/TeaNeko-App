package org.zexnocs.teanekoplugin.general.games.dice.event;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.zexnocs.teanekoplugin.general.games.dice.DiceService;
import org.zexnocs.teanekoplugin.general.games.dice.data.DiceSetData;

/**
 * 设置骰子事件，在设置骰子数据后触发
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@PluginOwned("general.games")
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
