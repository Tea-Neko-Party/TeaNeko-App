package org.zexnocs.teanekoclient.onebot.data.receive.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;

/**
 * 符合 onebot 规范的图片消息数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@TeaNekoContent(OnebotMessage.PREFIX + ImageOnebotContent.TYPE)
public class ImageOnebotContent implements ITeaNekoContent {
    public static final String TYPE = "image";

    // 图片文件名
    @JsonProperty("file")
    String file;

    // 图片 URL
    @JsonProperty("url")
    String url;

    // 图片类型
    @JsonProperty("subType")
    int subType;

    @JsonProperty("sub_type")
    int subType2;

    // 图片文件大小（字节）
    @JsonProperty("file_size")
    long fileSize;

    // ---- Lagrange 扩展 ----
    @JsonProperty("filename")
    String filename;
    @JsonProperty("summary")
    String summary;

    // ---- napcat 扩展 ----
    @JsonProperty("key")
    String key;

    /**
     * 转化成命令解析的字符串表示。
     * 例如 text 文字可以根据空格切割成多个字符串。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    public @NonNull String[] toCommandArgs() {
        return new String[]{"[CQ:image,url=" + url + "]"};
    }
}
