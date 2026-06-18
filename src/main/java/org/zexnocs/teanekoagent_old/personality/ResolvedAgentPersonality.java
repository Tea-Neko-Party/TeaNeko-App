package org.zexnocs.teanekoagent_old.personality;

import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityDefinition;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityRuntimeConfig;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord;
import org.zexnocs.teanekoagent_old.memory.PersonalityDeltaRecord;

import java.util.List;

/**
 * 单次请求解析完成后的 Agent 人格状态。
 *
 * @param agentId           实际使用的 agent ID。
 * @param source            active base personality 来源。
 * @param base              active base personality。
 * @param boundaryPolicy    人格边界策略。
 * @param personalityDeltas 已通过边界检查的性格学习修正。
 * @param memories          当前请求相关的长期记忆。
 * @param config            当前请求使用的人格运行配置。
 * @param modelOptions      当前请求的模型参数覆盖。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record ResolvedAgentPersonality(
        String agentId,
        PersonalitySource source,
        AgentPersonalityDefinition base,
        PersonalityBoundaryPolicy boundaryPolicy,
        List<PersonalityDeltaRecord> personalityDeltas,
        List<AgentMemoryRecord> memories,
        AgentPersonalityRuntimeConfig config,
        LLMModelOptions modelOptions
) {
    /**
     * 创建解析完成的人格状态。
     *
     * @param agentId           实际使用的 agent ID。
     * @param source            人格来源。
     * @param base              基础人格。
     * @param boundaryPolicy    边界策略。
     * @param personalityDeltas 性格修正列表。
     * @param memories          相关记忆列表。
     * @param config            运行配置。
     * @param modelOptions      模型参数覆盖。
     */
    public ResolvedAgentPersonality {
        personalityDeltas = personalityDeltas == null ? List.of() : List.copyOf(personalityDeltas);
        memories = memories == null ? List.of() : List.copyOf(memories);
        modelOptions = modelOptions == null ? LLMModelOptions.empty() : modelOptions;
    }
}
