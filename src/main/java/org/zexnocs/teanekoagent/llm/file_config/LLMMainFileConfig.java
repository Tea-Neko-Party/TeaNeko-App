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
 * <br>用于记录默认模型 ID 和各模型的默认调用参数。
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
     * 默认模型 ID。
     * <br>格式为 {@code provider/model}，用于 {@code LLMModelService.call(prompt)} 这类未显式指定模型的调用。
     */
    private String defaultModelId = "";

    /**
     * 各模型的默认配置项。
     * <br>配置项不会在加载阶段强校验是否已经注册到模型服务；只有调用对应模型时才会被使用。
     */
    private List<LLMModelFileConfigParameter> models = new ArrayList<>();

    /**
     * 获取默认模型 ID。
     *
     * @return 默认模型 ID
     */
    public Optional<LLMModelId> findDefaultModelId() {
        return parseModelId(defaultModelId);
    }

    /**
     * 按模型 ID 查找配置项。
     *
     * @param modelId 模型 ID
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
     * 解析模型 ID 字符串。
     *
     * @param value 模型 ID 字符串
     * @return 模型 ID
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
