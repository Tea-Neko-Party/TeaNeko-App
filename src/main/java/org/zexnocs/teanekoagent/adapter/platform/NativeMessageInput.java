package org.zexnocs.teanekoagent.adapter.platform;

import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Instant;

/**
 * 原生图文输入的不可变快照，引用和提及不伪装成用户原文或图片。
 * @param text 当前消息字面文本
 * @param message 冻结的有序图文消息
 * @param occurredAt 原生平台时刻
 * @param digest 完整图文的防重摘要，不只比较文字
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record NativeMessageInput(String text, ILLMMessage message, Instant occurredAt, String digest) {
    /**
     * 保存独立协议快照，平台消息之后的修改不影响模型输入。
     * @param text 字面原文
     * @param message 有序图文
     * @param occurredAt 原生时间
     * @param digest 防重摘要
     */
    public NativeMessageInput { message = LLMProtocolSnapshots.message(message); java.util.Objects.requireNonNull(occurredAt); }

    /**
     * 只复制当前消息的文本和 HTTP(S)/data 图片，不读取文件或下载资源。
     * @param data 原生消息
     * @return 完整快照，纯图像时原文为空字符串
     * @throws IllegalArgumentException 当图文超过界限或图片引用不合法时
     */
    public static NativeMessageInput from(ITeaNekoMessageData data) {
        var occurredAt = java.util.Objects.requireNonNull(data.getTime(), "原生消息时间不能为空。");
        var text = new StringBuilder(); var contents = new LLMContentListBuilder(); var digest = new java.util.ArrayList<String>();
        int images = 0, characters = 0;
        for (var item : data.getMessages()) {
            var part = item.getContentPart();
            if (part instanceof ITextTeaNekoContentPart words) {
                characters = Math.addExact(characters, words.getText().length());
                if (text.length() + words.getText().length() > 65536) { throw new IllegalArgumentException("原生文本超出容量限制。"); }
                text.append(words.getText()); contents.addText(words.getText()); digest.add("text"); digest.add(words.getText());
            } else if (part instanceof IImageTeaNekoContentPart image) {
                var url = image.getUrl();
                if (++images > 4 || url.length() > 2_097_152) { throw new IllegalArgumentException("原生图片数量或引用超出限制。"); }
                characters = Math.addExact(characters, url.length()); contents.addImage(url); digest.add("image"); digest.add(url);
            }
            if (characters > 2_097_152) { throw new IllegalArgumentException("原生图文总量超出限制。"); }
        }
        digest.add(occurredAt.toString());
        return new NativeMessageInput(text.toString(), LLMUserMessage.builder().contents(contents.build()).build(), occurredAt,
                StableDigest.sha256(digest.toArray(String[]::new)));
    }

    /**
     * 判断当前消息是否存在可送入模型的图文，不以提及或引用占位启动空模型请求。
     * @return 有实际图文内容时为真
     */
    public boolean hasContent() { return !text.isBlank() || message.getContents().stream().anyMatch(item -> "image".equals(item.getType())); }
}
