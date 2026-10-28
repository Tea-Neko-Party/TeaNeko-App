package org.zexnocs.teanekoagent.llm.framework.model;

import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;

import java.util.List;
import java.util.Objects;

/**
 * 统一消息结构与能力检查。未知内容明确拒绝，禁止静默丢弃或 toString 降级。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMInputValidator {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private LLMInputValidator() {}

    /**
     * 在 HTTP 发出前检查消息、内容类型及所选具体模型的图片角色和细节能力。
     *
     * @param messages 保持原始顺序的统一消息列表
     * @param capabilities 本次具体模型声明的输入能力
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static void validate(List<ILLMMessage> messages, ModelCapabilities capabilities) {
        Objects.requireNonNull(messages, "LLM messages");
        Objects.requireNonNull(capabilities, "model capabilities");
        for (var message : messages) {
            if (message == null || message.getRole() == null) {
                throw new IllegalArgumentException("LLM message and role are required.");
            }
            if (message.getContents() == null) {
                continue; // assistant 的纯工具调用可以没有正文。
            }
            for (var content : message.getContents()) {
                if (requirePart(content) instanceof ImageLLMContentPart image
                        && (!capabilities.imageInputRoles().contains(message.getRole())
                        || !capabilities.imageDetails().contains(image.getDetail()))) {
                    throw new IllegalArgumentException("Selected model adapter does not support this image role or detail level.");
                }
            }
        }
    }

    /**
     * 只接受已有明确映射的文本或图片内容，拒绝空项和类型与实际内容不匹配的数据。
     *
     * @param content 需要验证或映射的统一内容项
     * @return 已经明确支持并通过类型一致性检查的内容片段
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public static ILLMContentPart requirePart(ILLMContent content) {
        if (content != null) {
            var part = content.getContentPart();
            if ("text".equals(content.getType()) && part instanceof TextLLMContentPart text && text.getText() != null) {
                return part;
            }
            if ("image".equals(content.getType()) && part instanceof ImageLLMContentPart) {
                return part;
            }
        }
        throw new IllegalArgumentException("Unsupported or malformed LLM content; provide an explicit content mapper.");
    }
}
