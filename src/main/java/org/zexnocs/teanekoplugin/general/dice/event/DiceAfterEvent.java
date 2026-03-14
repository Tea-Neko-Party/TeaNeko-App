package org.zexnocs.teanekoplugin.general.dice.event;

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekoplugin.general.dice.data.DiceResultData;

/**
 * 骰子投掷后事件，已经获取到骰子结果数据，在通知处理器之后调用。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
public class DiceAfterEvent extends AbstractEvent<DiceResultData> {

    public DiceAfterEvent(DiceResultData data) {
        super(data);
    }

    /**
     * 在通知处理器之后调用的方法。
     */
    @Override
    public void _afterNotify() {
        var data = getData();
        // 是否使用默认播放
        if (!data.isUseDefaultNotification()) {
            return;
        }
        // 获取话术
        var message = _generateDiceMessage(data);
        data.getAttemptDiceData().getMessageData()
                .getMessageSender()
                .sendReplyMessage(message);
    }

    /**
     * 根据骰子结果数据生成话术消息。
     *
     * @param resultData 骰子结果数据
     * @return {@link String }
     */
    private String _generateDiceMessage(DiceResultData resultData) {
        StringBuilder builder = new StringBuilder();
        // 获取骰子数据
        var attemptDiceData = resultData.getAttemptDiceData();
        var diceData = attemptDiceData.getDiceData();
        var diceExpectedValue = attemptDiceData.getExpectedValue();
        var sigma = resultData.getSigma();
        var result = resultData.getDiceResultList();

        // 是否跳过期望值
        int maxValue = diceData.getDiceMaxValue();
        int count = diceData.getDiceCount();
        boolean skipExpectedValue = diceExpectedValue <= 0 || diceExpectedValue > maxValue;

        // 构造说明消息
        builder.append("当前投掷次数：").append(count).append("，最大值：").append(maxValue).append("，当前期望值：");
        if (skipExpectedValue) {
            builder.append("无\n");
        } else {
            builder.append(diceExpectedValue).append("\n");
        }

        // 根据期望值计算大成功和大失败的范围。
        int maxBigFailValue = (int) (diceExpectedValue * sigma);
        int minBigSuccessValue = maxValue - (int) (sigma * (maxValue - diceExpectedValue));

        builder.append("骰子结果：\n");

        // 是否成功
        boolean isSuccessful = false;

        for (int i = 0; i < result.size(); i++) {
            int resultValue = result.get(i);
            builder.append("第").append(i + 1).append("次：").append(resultValue);
            if (skipExpectedValue) {
                builder.append("\n");
                continue;
            }
            builder.append(", 结果: ");
            if (resultValue < maxBigFailValue) {
                builder.append("大失败！");
            } else if (resultValue < diceExpectedValue) {
                builder.append("失败。");
            } else if (resultValue <= minBigSuccessValue) {
                builder.append("成功。");
                isSuccessful = true;
            } else {
                builder.append("大成功！");
                isSuccessful = true;
            }
            builder.append("\n");
        }
        if (skipExpectedValue) {
            // 删除最后一个换行符
            if (!builder.isEmpty()) {
                builder.deleteCharAt(builder.length() - 1);
            }
        } else {
            // 添加最终结果
            if(isSuccessful) {
                builder.append("最终结果：成功！");
            } else {
                builder.append("最终结果：失败！");
            }
        }
        return builder.toString();
    }
}
