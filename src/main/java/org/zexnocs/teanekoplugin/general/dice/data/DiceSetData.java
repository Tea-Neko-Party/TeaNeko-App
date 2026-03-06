package org.zexnocs.teanekoplugin.general.dice.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

/**
 * 群聊骰子设置数据。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@AllArgsConstructor
@Getter
public class DiceSetData {
    /// 要回复的消息数据
    private ITeaNekoMessageData messageReceiveData;

    /// 骰子数量
    private int diceCount;

    /// 骰子最小值
    private int diceMaxValue;
}
