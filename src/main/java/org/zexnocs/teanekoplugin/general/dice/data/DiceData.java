package org.zexnocs.teanekoplugin.general.dice.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 骰子数据。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.0.1
 */
@AllArgsConstructor
@Getter
public class DiceData {
    /// 拥有这个骰子的 scope ID
    private final String scopeId;

    /// 骰子数量
    private final int diceCount;

    /// 骰子最大值
    private final int diceMaxValue;
}
