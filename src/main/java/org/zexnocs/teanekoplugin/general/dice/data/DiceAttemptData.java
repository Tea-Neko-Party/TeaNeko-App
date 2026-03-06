package org.zexnocs.teanekoplugin.general.dice.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

/**
 * 尝试投骰子数据。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Getter
@AllArgsConstructor
public class DiceAttemptData {
    /// 消息的原数据
    private final ITeaNekoMessageData messageData;

    /// 期望最小值
    private final int expectedValue;

    /// 骰子数据
    private final DiceData diceData;
}
