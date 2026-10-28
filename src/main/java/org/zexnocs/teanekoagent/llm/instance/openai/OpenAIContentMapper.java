package org.zexnocs.teanekoagent.llm.instance.openai;

import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import org.zexnocs.teanekoagent.llm.framework.model.LLMInputValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 及兼容协议的内容转换；供应商具体模型能力在模型边界校验。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class OpenAIContentMapper {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private OpenAIContentMapper() {}

    /**
     * 按统一内容顺序生成 Chat Completions 文本或图文数组，纯文本保留既有合并格式。
     *
     * @param message 需要发送或映射的消息
     * @return 纯文本内容或保持原图文顺序的协议数组，取决于输入是否包含图片
     */
    public static Object toChatContent(ILLMMessage message) {
        return map(message, false);
    }

    /**
     * 按统一内容顺序生成 Responses 内容块，不把图片引用拼接为普通字符串。
     *
     * @param message 需要发送或映射的消息
     * @return 纯文本内容或保持原图文顺序的协议数组，取决于输入是否包含图片
     */
    public static Object toResponsesContent(ILLMMessage message) {
        return map(message, true);
    }

    /**
     * 验证内容后选择协议块映射；仅在实际含图片时生成有序数组，保留纯文本兼容行为。
     *
     * @param message 需要发送或映射的消息
     * @param responses 是否生成 Responses 协议内容块
     * @return 纯文本内容或保持原图文顺序的协议数组，取决于输入是否包含图片
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private static Object map(ILLMMessage message, boolean responses) {
        if (message == null || message.getRole() == null) {
            throw new IllegalArgumentException("LLM message and role are required.");
        }
        var contents = message.getContents();
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        var blocks = new ArrayList<Map<String, Object>>();
        var text = new StringBuilder();
        boolean hasImage = false;
        for (var content : contents) {
            var part = LLMInputValidator.requirePart(content);
            if (part instanceof TextLLMContentPart textPart) {
                var value = textPart.getText();
                if (!value.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append(' ');
                    }
                    text.append(value);
                    blocks.add(Map.of("type", responses ? "input_text" : "text", "text", value));
                }
            } else if (part instanceof ImageLLMContentPart image) {
                // 当前统一协议只实现已核实的 user 图像输入；工具图像需另行扩展。
                if (message.getRole() != LLMMessageRole.USER) {
                    throw new IllegalArgumentException("This content mapper accepts images in user messages only.");
                }
                hasImage = true;
                if (responses) {
                    blocks.add(Map.of("type", "input_image", "image_url", image.getUrl(),
                            "detail", image.getDetail().getValue()));
                } else {
                    var source = image.getDetail() == LLMImageDetail.AUTO
                            ? Map.<String, Object>of("url", image.getUrl())
                            : Map.<String, Object>of("url", image.getUrl(), "detail", image.getDetail().getValue());
                    blocks.add(Map.of("type", "image_url", "image_url", source));
                }
            }
        }
        return hasImage ? List.copyOf(blocks) : text.toString();
    }
}
