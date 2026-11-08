package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;

import java.util.Set;

/**
 * 某一具体模型在当前适配器中的输入能力，不代表整个供应商的能力。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param imageInputRoles 允许图片输入的消息角色集合
 * @param imageDetails 具体模型已验证的细节级别集合
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record ModelCapabilities(Set<LLMMessageRole> imageInputRoles, Set<LLMImageDetail> imageDetails) {
    /**
     * 冻结具体模型的图片角色与细节能力，要求两组能力同时存在或同时为空。
     *
     * @param imageInputRoles 允许图片输入的消息角色集合
     * @param imageDetails 具体模型已验证的细节级别集合
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public ModelCapabilities {
        imageInputRoles = Set.copyOf(imageInputRoles);
        imageDetails = Set.copyOf(imageDetails);
        if (imageInputRoles.isEmpty() != imageDetails.isEmpty()) {
            throw new IllegalArgumentException("Image roles and detail levels must both be present or absent.");
        }
    }

    /**
     * 声明尚未验证图片协议的文本能力，不按供应商或名称前缀推测视觉支持。
     *
     * @return 按实际模型或显式能力集合构造的不可变能力声明
     */
    public static ModelCapabilities textOnly() {
        return new ModelCapabilities(Set.of(), Set.of());
    }

    /**
     * 声明仅用户角色可以携带指定细节级别的图片，并固定能力集合。
     *
     * @param details 允许的图片细节级别
     * @return 按实际模型或显式能力集合构造的不可变能力声明
     */
    public static ModelCapabilities userImages(Set<LLMImageDetail> details) {
        return new ModelCapabilities(Set.of(LLMMessageRole.USER), details);
    }

    /**
     * 根据显式声明判断是否开放图片输入，未知模型保持文本能力。
     *
     * @return 已经显式声明图片输入角色时为 true
     */
    public boolean supportsImages() {
        return !imageInputRoles.isEmpty();
    }
}
