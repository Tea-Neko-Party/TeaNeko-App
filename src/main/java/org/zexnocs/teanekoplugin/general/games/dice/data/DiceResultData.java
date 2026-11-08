package org.zexnocs.teanekoplugin.general.games.dice.data;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.util.List;

/**
 * 骰子结果数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Getter
@Builder
@PluginOwned("general.games")
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
