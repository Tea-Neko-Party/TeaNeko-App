package org.zexnocs.teanekoagent.llm.framework.tool;

import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import java.util.List;
import java.util.Objects;

/**
 * 工具显式声明的文本及视觉参考，图片不混入文本字符预算，也不从任意正文猜测 URL。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param text 工具文字结果
 * @param images 仅供本轮模型识别的已校验图片
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.0-l
 */
public record LLMToolOutput(String text, List<ImageLLMContentPart> images) {
    /**
     * 兼容原有纯文本工具。
     * @param text 原工具文字
     */
    public LLMToolOutput(String text) { this(text, List.of()); }

    /**
     * 固定图片集合，复用原生消息的四图和两百万字符总引用上限，不下载资源。
     * @param text 文字结果
     * @param images 不可变图像引用
     * @throws IllegalArgumentException 当图片数量或总引用长度超限时
     */
    public LLMToolOutput {
        Objects.requireNonNull(text, "工具文字结果不能为空。"); images = List.copyOf(images);
        if (images.size() > 4 || images.stream().mapToLong(image -> image.getUrl().length()).sum() > 2_097_152) {
            throw new IllegalArgumentException("工具图片数量或引用总长度超出限制。");
        }
    }
}
