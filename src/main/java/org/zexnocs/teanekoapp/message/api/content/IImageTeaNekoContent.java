package org.zexnocs.teanekoapp.message.api.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

/**
 * 图片消息内容接口，表示一个图片消息的内容。
 * <p>用于描述图片来源（URL/base64/file 等）。</p>
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
public interface IImageTeaNekoContent extends ITeaNekoContent {
    /// 类型字符串常量。
    String TYPE = "image";

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
    @NonNull String getUrl();

    /**
     * 获取类型。
     * 该方法应当加上 {@link com.fasterxml.jackson.annotation.JsonIgnore} 注解防止被序列化。
     *
     * @return {@link String} 类型字符串
     */
    @NonNull
    @JsonIgnore
    @Override
    default String getType() {
        return TYPE;
    }

    /**
     * 转化成命令解析的字符串表示。
     * <p>默认返回图片 URL，若为空则返回空数组。</p>
     *
     * @return {@link String[]} 转化后的字符串数组
     */
    @Override
    @NonNull
    default String[] toCommandArgs() {
        return new String[]{getUrl()};
    }
}