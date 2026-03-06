package org.zexnocs.teanekoplugin.general.dice.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 骰子结果数据。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Getter
@Builder
public class DiceResultData {
    /// 尝试投骰子数据
    private final DiceAttemptData attemptDiceData;

    /// 骰子结果
    private final List<Integer> diceResultList;

    /// 判断大成功、大失败范围的标准。
    @Builder.Default
    private final double sigma = 0.2;

    /// 是否使用默认通知
    @Setter
    @Builder.Default
    private boolean useDefaultNotification = true;
}
