package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.llm.LLMComponent;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter;
import tools.jackson.databind.JsonNode;

/**
 * 视觉模型选择的校验及受保护配置转换，复用原 LLM 组件协议。
 * <br>2026/11/10：组件继承与全局未设置分别声明，不把凭据放入运行参数。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Component
public final class StickerModelConfiguration {
    private final LLMComponent llm;

    /** @param llm 原模型配置校验及环境变量解析器 */
    public StickerModelConfiguration(LLMComponent llm) {
        this.llm = llm;
    }

    /**
     * @param selection 完整模型选择
     * @param defaultSource 此位置允许的默认来源，inherit 或 none
     * @throws IllegalArgumentException 当来源或配置不合法时
     */
    public void validate(JsonNode selection, String defaultSource) {
        if (!selection.isObject()) {
            throw new IllegalArgumentException("视觉模型设置必须是对象。");
        }
        String source = selection.path("source").asString(defaultSource);
        // 2026/11/10：全局文件按字段编辑，未选中的旧私有配置保留但不参与校验和使用。
        if (defaultSource.equals("none") && !source.equals("custom")) {
            var active = (tools.jackson.databind.node.ObjectNode) selection.deepCopy();
            active.remove("configuration");
            if (source.equals("none")) {
                active.remove("route");
            }
            selection = active;
        }
        if (source.equals(defaultSource)) {
            if (selection.size() > 1 || selection.size() == 1 && !selection.has("source")) {
                throw new IllegalArgumentException("继承或未设置模型时不接受其他模型参数。");
            }
        } else {
            llm.validate(selection);
        }
    }

    /**
     * @param selection 原受保护的私有选择
     * @return 展开环境变量后的私有模型，禁止返回给公开页面
     */
    public LLMModelFileConfigParameter configuration(JsonNode selection) {
        return llm.resolveConfiguration(selection);
    }
}
