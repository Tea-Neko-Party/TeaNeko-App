package org.zexnocs.teanekoagent_old.llm.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelId;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agent 模型文件配置。
 * <br>该配置用于在 {@code config/agent/model.yml} 中声明各模型适配器的默认调用参数。
 * <br>默认模型适配器 ID 属于 Agent 主配置，存放在 {@code config/agent/main-config.yml}。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
@FileConfig(
        value = "model",
        path = "agent",
        type = FileConfigType.YAML
)
@Getter
@Setter
@NoArgsConstructor
public class LLMModelFileConfig implements IFileConfigData {
    /**
     * 各模型适配器的默认配置项。
     * <br>配置项不会在加载阶段强校验是否已经注册到模型服务；只有调用对应 ID 时才会被使用。
     * <br>新增供应商时，在该列表中添加一项，并确保 {@code id} 与模型适配器注册 ID 一致，例如 {@code deepseek}。
     */
    private List<LLMModelFileConfigParameter> models = new ArrayList<>();

    /**
     * 按模型适配器 ID 查找配置项。
     *
     * @param modelId 模型适配器 ID。
     * @return 对应配置项。
     */
    public Optional<LLMModelFileConfigParameter> findModelConfig(LLMModelId modelId) {
        var models = getModels();
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
     * @param value 模型适配器 ID 字符串。
     * @return 模型适配器 ID。
     */
    static Optional<LLMModelId> parseModelId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LLMModelId.of(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
