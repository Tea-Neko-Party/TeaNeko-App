package org.zexnocs.teanekoagent.llm.framework.message.content;

import org.zexnocs.teanekoagent.llm.framework.message.LLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于快速构造 LLM Content List 的 builder
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
public class LLMContentListBuilder {
    /**
     * 直接获取一个 builder
     *
     * @return 新的内容构建器，与其他构建过程保持独立
     */
    public static LLMContentListBuilder builder() {
        return new LLMContentListBuilder();
    }

    /// 存储的 list
    private final List<ILLMContent> contentList = new ArrayList<>();

    /**
     * 构造出 list
     *
     * @return 固定当前顺序的不可变内容列表
     */
    public List<ILLMContent> build() {
        return List.copyOf(contentList);
    }

    /**
     * 添加一个 {@link TextLLMContentPart}
     *
     * @param text 文本内容
     * @return 当前 builder 对象，以便于链式调用
     */
    public LLMContentListBuilder addText(String text) {
        contentList.add(LLMContent.builder()
                .type("text")
                .contentPart(new TextLLMContentPart(text))
                .build());
        return this;
    }

    /**
     * 将已校验的图片引用按当前顺序加入内容，保持与相邻文本的排列关系。
     *
     * @param url HTTP(S) 图片引用或受支持的内联数据引用
     * @return 当前构建器，供调用方继续组合后提交
     */
    public LLMContentListBuilder addImage(String url) {
        return addImage(url, LLMImageDetail.AUTO);
    }

    /**
     * 将已校验的图片引用按当前顺序加入内容，保持与相邻文本的排列关系。
     *
     * @param url HTTP(S) 图片引用或受支持的内联数据引用
     * @param detail 模型协议使用的图片细节级别
     * @return 当前构建器，供调用方继续组合后提交
     */
    public LLMContentListBuilder addImage(String url, LLMImageDetail detail) {
        return addImagePart(new ImageLLMContentPart(url, detail));
    }

    /**
     * 复制并编码受支持的图片字节后加入内容；不将可变字节数组保存在结果中。
     *
     * @param mimeType 受支持的图片媒体类型
     * @param bytes 待复制并编码的图片字节
     * @return 当前构建器，供调用方继续组合后提交
     */
    public LLMContentListBuilder addImageBytes(String mimeType, byte[] bytes) {
        return addImagePart(ImageLLMContentPart.fromBytes(mimeType, bytes, LLMImageDetail.AUTO));
    }

    /**
     * 将图片包装为统一内容项，供供应商 mapper 处理协议差异。
     *
     * @param image 已经校验的不可变图片内容
     * @return 当前构建器，供调用方继续组合后提交
     */
    private LLMContentListBuilder addImagePart(ImageLLMContentPart image) {
        contentList.add(LLMContent.builder().type("image").contentPart(image).build());
        return this;
    }
}
