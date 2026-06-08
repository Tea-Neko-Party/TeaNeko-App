package org.zexnocs.teanekoagent.llm.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 大语言模型主文件配置。
 * <br>用于记录默认模型适配器 ID 和各供应商适配器的默认调用参数。
 *
 * @author zExNocs
 * @date 2026/06/08
 * @since 4.4.0
 */
@FileConfig(
        value = "main-config",
        path = "llm",
        type = FileConfigType.YAML
)
@Getter
@Setter
@NoArgsConstructor
public class LLMMainFileConfig implements IFileConfigData {
    /**
     * 默认模型适配器 ID。
     * <br>该值通常与供应商 ID 相同，例如 {@code openai}、{@code deepseek}。
     * <br>用于 {@code LLMModelService.call(prompt)} 这类未显式指定供应商的调用；具体模型名称由模型代码默认值或配置项 {@code model} 决定。
     */
    private String defaultModelId = "";

    /**
     * 各模型适配器的默认配置项。
     * <br>配置项不会在加载阶段强校验是否已经注册到模型服务；只有调用对应 ID 时才会被使用。
     * <br>新增供应商时，在该列表中添加一项，并确保 {@code id} 与模型适配器注册 ID 一致，例如 {@code deepseek}。
     */
    private List<LLMModelFileConfigParameter> models = new ArrayList<>();

    /**
     * 获取默认模型适配器 ID。
     *
     * @return 默认模型适配器 ID
     */
    public Optional<LLMModelId> findDefaultModelId() {
        return parseModelId(defaultModelId);
    }

    /**
     * 按模型适配器 ID 查找配置项。
     *
     * @param modelId 模型适配器 ID
     * @return 对应配置项
     */
    public Optional<LLMModelFileConfigParameter> findModelConfig(LLMModelId modelId) {
        if (modelId == null || models == null) {
            return Optional.empty();
        }
        return models.stream()
                .filter(modelConfig -> modelConfig.findModelId()
                        .filter(modelId::equals)
                        .isPresent())
                .findFirst();
    }

    /**
     * 解析模型适配器 ID 字符串。
     *
     * @param value 模型适配器 ID 字符串
     * @return 模型适配器 ID
     */
    static Optional<LLMModelId> parseModelId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LLMModelId.parse(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
