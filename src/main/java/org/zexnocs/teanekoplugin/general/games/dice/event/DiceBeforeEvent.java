package org.zexnocs.teanekoplugin.general.games.dice.event;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.zexnocs.teanekoplugin.general.games.dice.DiceService;
import org.zexnocs.teanekoplugin.general.games.dice.data.DiceAttemptData;

/**
 * 骰子事件，在骰子结果生成前触发
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@PluginOwned("general.games")
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
