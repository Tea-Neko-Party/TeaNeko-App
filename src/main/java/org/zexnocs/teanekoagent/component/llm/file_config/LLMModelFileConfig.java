package org.zexnocs.teanekoagent.component.llm.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Agent 模型文件配置。
 * <br>在 {@code config/agent/model.yml} 统一声明调用参数与原始凭据配置，凭据只在请求边界解析。
 * <br>默认模型路由、各供应商参数、凭据、连接与 metadata 均由同一文件维护。
 *
 * <br>2026/11/04：说明配置路由唯一性与同协议多模型的文件格式。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
@FileConfig(
        value = "model",
        path = "agent",
        type = FileConfigType.YAML,
        sensitive = true
)
@Getter
@Setter
@NoArgsConstructor
public class LLMModelFileConfig implements IFileConfigData {
    /** 默认路由；为空时独立宿主须在 Options 中明确指定供应商。 */
    private String defaultModelId = "";
    /**
     * 各唯一模型路由的配置项。
     * <br>配置项不会在加载阶段强校验是否已经注册到模型服务；只有调用对应 ID 时才会被使用。
     * <br>2026/11/04：唯一 {@code id} 用于选择配置，{@code adapter} 指定实际注册协议；省略协议时沿用同名路由。
     */
    private List<LLMModelFileConfigParameter> models = new ArrayList<>();

    /**
     * 读取同一模型文件的默认路由，不再依赖 Agent 主配置。
     * @return 默认路由，未配置时为空
     */
    public Optional<LLMModelId> findDefaultModelId() { return parseModelId(defaultModelId); }

    /**
     * 按唯一模型配置路由 ID 查找配置项；同一协议可以有多条不同路由。
     *
     * @param modelId 模型配置路由 ID。
     * @return 对应配置项。
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    public Optional<LLMModelFileConfigParameter> findModelConfig(LLMModelId modelId) {
        var models = getModels();
        if (modelId == null || models == null) {
            throw new LLMConfigurationException("model.yml 必须提供模型列表及所选路由。");
        }
        var ids = new HashSet<LLMModelId>();
        for (var entry : models) {
            if (entry == null || entry.findModelId().isEmpty() || !ids.add(entry.findModelId().orElseThrow())) {
                throw new LLMConfigurationException("model.yml 存在空模型条目或重复路由。");
            }
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
