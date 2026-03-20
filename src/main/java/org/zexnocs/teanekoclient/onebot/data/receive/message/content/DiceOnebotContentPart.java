package org.zexnocs.teanekoclient.onebot.data.receive.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.TeaNekoContentPart;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;

/**
 * onebot 协议中的骰子消息数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TeaNekoContentPart(OnebotContent.PREFIX + DiceOnebotContentPart.TYPE)
public class DiceOnebotContentPart implements ITeaNekoContentPart {
    public static final String TYPE = "dice";

    @JsonProperty("result")
    private int result;

    /**
     * 转化成命令解析的字符串表示。
     * 例如 text 文字可以根据空格切割成多个字符串。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    public @NonNull String[] toCommandArgs() {
        return new String[]{"[CQ:dice,result=" + result + "]"};
    }

    /**
     * 获取到原始文本。
     *
     * @return {@link String} 原始文本。
     */
    @Override
    public @NonNull String toRawString() {
        return "[CQ:dice,result=" + result + "]";
    }
}
