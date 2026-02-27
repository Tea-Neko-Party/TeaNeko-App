package org.zexnocs.teanekoapp.message.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.TeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContent;

/**
 * 图片消息内容类，表示一个图片消息的内容。
 * <p>如果 api 不同，则平台自行实现 {@link IImageTeaNekoContent}</p>
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@AllArgsConstructor
@TeaNekoContent(TeaNekoMessage.PREFIX + IImageTeaNekoContent.TYPE)
public class ImageTeaNekoContent implements IImageTeaNekoContent {
    /// 图片来源 URL（file/http/base64）
    @JsonProperty("url")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String url;

    /// 同 url，可能有一个为 null
    @JsonProperty("file")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String file;

    /**
     * 获取图片来源 URL。
     * <p>可能为以下格式之一：</p>
     * <ul>
     *     <li>本地路径："file://D:/a.jpg"</li>
     *     <li>网络路径："http://" 或 "https://"</li>
     *     <li>base64："base64://base64字符串"</li>
     * </ul>
     *
     * @return {@link String} 图片来源 URL，可能为 null
     */
    @Override
    public @NonNull String getUrl() {
        return url != null ? url : file;
    }
}