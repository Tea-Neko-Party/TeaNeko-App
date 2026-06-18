package org.zexnocs.teanekoagent_old.file_config;

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
 * @date 2026/06/10
 * @since 4.4.1
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
    /**
     * 默认模型适配器 ID。
     * <br>该值通常与供应商 ID 相同，例如 {@code deepseek}。
     * <br>为空时，调用方必须在 prompt options 中显式指定 provider。
     */
    private String defaultModelId = "";

    /**
     * 是否启用 Agent 受控思考流程。
     * <br>启用后每次模型调用使用结构化决策输出，并在有限步骤内完成工具调用和最终回答。
     */
    private boolean thinkingEnabled = true;

    /**
     * 单轮对话允许的最大模型思考步骤数。
     * <br>建议使用 2 到 4；数值越大越可能执行更多工具调用，同时会增加 token 消耗
     */
    private int maxThinkingSteps = 3;

    /**
     * 单轮回答最多允许的模型思考步骤数，包含最后生成答案的步骤
     * <br>该值用于避免思考摘要占用过多 completion token
     */
    private int maxThoughtSummaryLength = 240;

    /**
     * 是否在 {@code AgentOutput} 中保留可公开思考摘要。
     * <br>关闭后仍会执行受控思考流程，但最终输出只保留答案和元数据。
     */
    private boolean includeThoughtsInOutput = true;

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

    /**
     * 获取规范化最大思考步骤数。
     *
     * @return 1 到 8 之间的最大思考步骤数
     */
    public int normalizedMaxThinkingSteps() {
        return Math.clamp(maxThinkingSteps, 1, 8);
    }

    /**
     * 获取规范化思考摘要最大字符数。
     *
     * @return 32 到 1000 之间的思考摘要最大字符数
     */
    public int normalizedMaxThoughtSummaryLength() {
        return Math.clamp(maxThoughtSummaryLength, 32, 1024);
    }
}
