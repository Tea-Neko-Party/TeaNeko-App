package org.zexnocs.teanekoplugin.general.games.dice.data;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.plugin.PluginOwned;

/**
 * 群聊骰子设置数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@AllArgsConstructor
@Getter
@PluginOwned("general.games")
public class DiceSetData {
    /// 要回复的消息数据
    private ITeaNekoMessageData messageReceiveData;

    /// 骰子数量
    private int diceCount;

    /// 骰子最小值
    private int diceMaxValue;
}
