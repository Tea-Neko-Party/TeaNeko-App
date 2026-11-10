package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一消息结构与能力检查。未知内容明确拒绝，禁止静默丢弃或 toString 降级。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：报告首个失败字段的位置、允许能力与实际结构，不输出原文或图片引用。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.1-l
 */
public final class LLMInputValidator {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private LLMInputValidator() {}

    /**
     * 在 HTTP 发出前检查消息、内容类型及所选具体模型的图片角色和细节能力。
     * 2026/11/10：保留首个失败位置及完整角色、细节对照，位置下标从零开始。
     *
     * @param messages 保持原始顺序的统一消息列表
     * @param capabilities 本次具体模型声明的输入能力
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static void validate(List<ILLMMessage> messages, ModelCapabilities capabilities) {
        Objects.requireNonNull(messages, "LLM 消息列表不能为空。");
        Objects.requireNonNull(capabilities, "模型输入能力不能为空。");
        int messageIndex = 0;
        for (var message : messages) {
            String path = "messages[" + messageIndex++ + "]";
            if (message == null) {
                throw new LLMInputValidationException(path, "非空消息", "null");
            }
            if (message.getRole() == null) {
                throw new LLMInputValidationException(path + ".role", "SYSTEM、USER、ASSISTANT 或 TOOL", "null");
            }
            if (message.getContents() == null) {
                continue; // assistant 的纯工具调用可以没有正文。
            }
            int contentIndex = 0;
            for (var content : message.getContents()) {
                String field = path + ".contents[" + contentIndex++ + "]";
                if (requirePart(content, field) instanceof ImageLLMContentPart image
                        && (!capabilities.imageInputRoles().contains(message.getRole())
                        || !capabilities.imageDetails().contains(image.getDetail()))) {
                    throw new LLMInputValidationException(field + ".image",
                            "允许图片角色=" + values(capabilities.imageInputRoles())
                                    + "，允许图片细节=" + values(capabilities.imageDetails())
                                    + (capabilities.supportsImages() ? "" : "（当前模型未声明图片输入能力）"),
                            "图片角色=" + message.getRole().name() + "，图片细节=" + image.getDetail().name());
                }
            }
        }
    }

    /**
     * 只接受已有明确映射的文本或图片内容，拒绝空项和类型与实际内容不匹配的数据。
     * 2026/11/10：非完整消息校验也提供安全结构对照，不回显未知类型的原始值。
     *
     * @param content 需要验证或映射的统一内容项
     * @return 已经明确支持并通过类型一致性检查的内容片段
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public static ILLMContentPart requirePart(@Nullable ILLMContent content) {
        return requirePart(content, "content");
    }

    /**
     * 按明确位置验证内容声明和实体，所有错误值均投影为固定类别。
     * @param content 外部内容项，允许为空以给出明确诊断
     * @param field 宿主构造的字段位置
     * @return 已验证的文本或图片实体
     * @throws LLMInputValidationException 当内容缺失、未知或类型不匹配时
     */
    private static ILLMContentPart requirePart(@Nullable ILLMContent content, String field) {
        if (content == null) {
            throw new LLMInputValidationException(field, "非空文本或图片内容", "null");
        }
        var type = content.getType();
        var part = content.getContentPart();
        if ("text".equals(type) && part instanceof TextLLMContentPart text && text.getText() != null) {
            return part;
        }
        if ("image".equals(type) && part instanceof ImageLLMContentPart) {
            return part;
        }
        String declared = "text".equals(type) || "image".equals(type) ? type : "未知类型（原值已隐藏）";
        String actual = switch (part) {
            case TextLLMContentPart text -> text.getText() == null ? "文本（text=null）" : "文本";
            case ImageLLMContentPart image -> "图片";
            default -> "未支持的内容实体";
        };
        throw new LLMInputValidationException(field,
                "type=text 且文本非空，或 type=image 且实体为图片",
                "类型声明=" + declared + "，内容实体=" + actual);
    }

    /**
     * 固定枚举集合的显示顺序，不接受外部原始字符串。
     * @param values 模型声明的枚举能力
     * @return 按名称排序的有界枚举集合
     */
    private static String values(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(", ", "[", "]"));
    }
}
