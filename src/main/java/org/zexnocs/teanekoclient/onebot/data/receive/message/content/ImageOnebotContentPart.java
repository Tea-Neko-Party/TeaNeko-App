package org.zexnocs.teanekoclient.onebot.data.receive.message.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;

/**
 * 符合 onebot 规范的图片消息数据类。
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
@TeaNekoContent(OnebotContent.PREFIX + IImageTeaNekoContentPart.TYPE)
public class ImageOnebotContentPart implements IImageTeaNekoContentPart {
    // 图片文件名
    @JsonProperty("file")
    private String file;

    // 图片 URL
    @JsonProperty("url")
    private String url;

    // 图片类型
    @JsonProperty("subType")
    private int subType;

    @JsonProperty("sub_type")
    private int subType2;

    // 图片文件大小（字节）
    @JsonProperty("file_size")
    private long fileSize;

    // ---- Lagrange 扩展 ----
    @JsonProperty("filename")
    private String filename;

    @JsonProperty("summary")
    private String summary;

    // ---- napcat 扩展 ----
    @JsonProperty("key")
    private String key;
}
