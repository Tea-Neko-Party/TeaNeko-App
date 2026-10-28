package org.zexnocs.teanekoagent.llm.framework.message.content;

import lombok.Getter;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContentPart;

import java.net.URI;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变图像引用。只保存 HTTP(S)/data URL，不读取文件、不下载远程资源。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter
public final class ImageLLMContentPart implements ILLMContentPart {
    private static final Set<String> MIME_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    // 统一内联表示的内存上限；供应商可另有更严格的请求限制。
    public static final int MAX_INLINE_BYTES = 32 * 1024 * 1024;

    private final String url;
    private final LLMImageDetail detail;

    /**
     * 校验图片引用和细节级别，保存不可变输入表示；不读取本地文件或主动下载远程图片。
     *
     * @param url HTTP(S) 图片引用或受支持的内联数据引用
     */
    public ImageLLMContentPart(String url) {
        this(url, LLMImageDetail.AUTO);
    }

    /**
     * 校验图片引用和细节级别，保存不可变输入表示；不读取本地文件或主动下载远程图片。
     *
     * @param url HTTP(S) 图片引用或受支持的内联数据引用
     * @param detail 模型协议使用的图片细节级别
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public ImageLLMContentPart(String url, LLMImageDetail detail) {
        this.url = validateUrl(url);
        this.detail = Objects.requireNonNull(detail, "image detail");
    }

    /**
     * 将受支持的图片字节转换为受长度限制的 base64 引用，不保留调用方可变数组。
     *
     * @param mimeType 受支持的图片媒体类型
     * @param bytes 待复制并编码的图片字节
     * @param detail 模型协议使用的图片细节级别
     * @return 已复制并校验图片引用的不可变内容片段
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public static ImageLLMContentPart fromBytes(String mimeType, byte[] bytes, LLMImageDetail detail) {
        if (mimeType == null || !MIME_TYPES.contains(mimeType) || bytes == null || bytes.length == 0 || bytes.length > MAX_INLINE_BYTES) {
            throw new IllegalArgumentException("Invalid inline image format or size.");
        }
        return new ImageLLMContentPart("data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes), detail);
    }

    /**
     * 校验 HTTP(S) 或受支持的内联图片引用，拒绝本地路径、非法编码和超限数据且不回显原引用。
     *
     * @param value 待处理的值
     * @return 按本方法契约生成的文本或标识
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private static String validateUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Image URL is required.");
        }
        try {
            if (value.startsWith("data:")) {
                int separator = value.indexOf(";base64,");
                if (separator < 0 || !MIME_TYPES.contains(value.substring(5, separator))) {
                    throw new IllegalArgumentException();
                }
                var data = value.substring(separator + 8);
                if (data.isEmpty() || data.length() > 4L * ((MAX_INLINE_BYTES + 2L) / 3L) || data.length() % 4 != 0) {
                    throw new IllegalArgumentException();
                }
                var decoded = Base64.getDecoder().decode(data);
                if (decoded.length == 0 || decoded.length > MAX_INLINE_BYTES) {
                    throw new IllegalArgumentException();
                }
            } else {
                var uri = URI.create(value);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
                    throw new IllegalArgumentException();
                }
            }
            return value;
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Invalid image URL or inline image data.");
        }
    }
}
