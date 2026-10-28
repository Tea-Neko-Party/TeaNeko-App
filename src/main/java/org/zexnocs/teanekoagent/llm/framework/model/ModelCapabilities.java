package org.zexnocs.teanekoagent.llm.framework.model;

import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;

import java.util.Set;

/**
 * 某一具体模型在当前适配器中的输入能力，不代表整个供应商的能力。
 *
 * @param imageInputRoles 允许图片输入的消息角色集合
 * @param imageDetails 具体模型已验证的细节级别集合
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
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
