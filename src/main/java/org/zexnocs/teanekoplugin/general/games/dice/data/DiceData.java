package org.zexnocs.teanekoplugin.general.games.dice.data;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekocore.plugin.PluginOwned;

/**
 * 骰子数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.0.1
 * @version 4.9.0-l
 */
@AllArgsConstructor
@Getter
@PluginOwned("general.games")
public class DiceData {
    /// 拥有这个骰子的 scope ID
    private final String scopeId;

    /// 骰子数量
    private final int diceCount;

    /// 骰子最大值
    private final int diceMaxValue;
}
