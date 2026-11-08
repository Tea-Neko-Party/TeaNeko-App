package org.zexnocs.teanekoplugin.general.games.dice.data;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.plugin.PluginOwned;

/**
 * 尝试投骰子数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Getter
@AllArgsConstructor
@PluginOwned("general.games")
public class DiceAttemptData {
    /// 消息的原数据
    private final ITeaNekoMessageData messageData;

    /// 期望最小值
    private final int expectedValue;

    /// 骰子数据
    private final DiceData diceData;
}
