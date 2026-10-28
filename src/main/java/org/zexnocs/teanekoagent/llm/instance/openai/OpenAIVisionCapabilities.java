package org.zexnocs.teanekoagent.llm.instance.openai;

import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;

import java.util.Set;

/**
 * 已核对的精确模型名；不通过 gpt-* 前缀推断未知模型的视觉能力。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class OpenAIVisionCapabilities {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private OpenAIVisionCapabilities() {}

    private static final Set<String> ORIGINAL = Set.of("gpt-5.5", "gpt-5.5-2026-04-23", "gpt-5.4",
            "gpt-5.4-mini", "gpt-5.4-nano");
    private static final Set<String> STANDARD = Set.of("gpt-5.2", "gpt-5.1", "gpt-4.1", "gpt-4.1-mini",
            "gpt-4o", "gpt-4o-mini");

    /**
     * 按已验证的精确模型名声明图片细节能力；未知名称不按前缀推断支持。
     *
     * @param model 本次实际选中的具体模型名称
     * @return 按实际模型或显式能力集合构造的不可变能力声明
     */
    public static ModelCapabilities forModel(String model) {
        if (model != null && ORIGINAL.contains(model)) {
            return ModelCapabilities.userImages(Set.of(LLMImageDetail.values()));
        }
        if (model != null && STANDARD.contains(model)) {
            return ModelCapabilities.userImages(Set.of(LLMImageDetail.AUTO, LLMImageDetail.LOW, LLMImageDetail.HIGH));
        }
        return ModelCapabilities.textOnly();
    }
}
