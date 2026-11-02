package org.zexnocs.teanekoagent.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.Optional;

/**
 * Agent 主文件配置。
 * <br>该配置存放 agent 运行时共享配置，不包含各模型适配器的详细默认参数。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
@FileConfig(
        value = "main-config",
        path = "agent",
        type = FileConfigType.YAML
)
@Getter
@Setter
@NoArgsConstructor
public class AgentMainFileConfig implements IFileConfigData {
    /** 本地 Agent 总开关，关闭时不继续检查模型或凭据。 */
    private boolean enabled = true;

    /**
     * 默认模型适配器 ID。
     * <br>该值通常与供应商 ID 相同，例如 {@code deepseek}。
     * <br>为空时，调用方必须在 prompt options 中显式指定 provider。
     */
    private String defaultModelId = "";


    /**
     * 查找默认模型适配器 ID。
     *
     * @return 默认模型适配器 ID。
     */
    public Optional<LLMModelId> findDefaultModelId() {
        if (defaultModelId == null || defaultModelId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LLMModelId.of(defaultModelId));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

}
