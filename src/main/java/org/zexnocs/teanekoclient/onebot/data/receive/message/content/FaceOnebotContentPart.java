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
 * 符合 onebot 规范的表情消息数据类。
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
@TeaNekoContentPart(OnebotContent.PREFIX + FaceOnebotContentPart.TYPE)
public class FaceOnebotContentPart implements ITeaNekoContentPart {
    public static final String TYPE = "face";

    @JsonProperty("id")
    private String id;

    @JsonProperty("large")
    private String large;

    /**
     * 转化成命令解析的字符串表示。
     * 例如 text 文字可以根据空格切割成多个字符串。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    public @NonNull String[] toCommandArgs() {
        return new String[]{"[CQ:face,id=" + id + "]"};
    }

    /**
     * 获取到原始文本。
     *
     * @return {@link String} 原始文本。
     */
    @Override
    public @NonNull String toRawString() {
        return "[CQ:face,id=" + id + "]";
    }
}
