package org.zexnocs.teanekoagent_old.personality;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentRequestContext;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityDefinition;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityFileConfigService;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryQueryService;
import org.zexnocs.teanekoagent_old.personality.config.IAgentPersonalityConfigPort;

/**
 * Agent 人格解析服务。
 * <br>负责按优先级解析 active base personality、学习修正、长期记忆和模型参数覆盖。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class AgentPersonalityResolver {
    /**
     * 人格配置读取端口。
     * <br>该端口由应用适配层实现，Agent Core 不直接依赖 TeaNeko App 配置服务。
     */
    private final IAgentPersonalityConfigPort configPort;

    /**
     * 文件主性格配置服务。
     */
    private final AgentPersonalityFileConfigService fileConfigService;

    /**
     * Agent 长期记忆查询服务。
     */
    private final AgentMemoryQueryService memoryQueryService;

    /**
     * 模型参数覆盖解析服务。
     */
    private final AgentLLMModelOptionsResolver modelOptionsResolver;

    /**
     * 解析单次请求实际使用的人格状态。
     *
     * @param context Agent 请求上下文。
     * @return 已解析的人格状态。
     */
    public ResolvedAgentPersonality resolve(AgentRequestContext context) {
        var initialConfig = configPort.getConfig(context.scopeId(), context.agentId());
        var agentId = resolveAgentId(context.agentId(), initialConfig.agentId());
        var config = context.agentId().isBlank() && !initialConfig.agentId().isBlank()
                ? configPort.getConfig(context.scopeId(), agentId)
                : initialConfig;

        var source = PersonalitySource.FILE;
        final AgentPersonalityDefinition base;
        if (config.hasCustomPersonality()) {
            base = AgentPersonalityDefinition.fromCustom(config, agentId);
            source = PersonalitySource.CUSTOM_CONFIG;
        } else {
            base = fileConfigService.getPersonality(agentId);
            if (base.getId() == null || base.getId().isBlank()) {
                source = PersonalitySource.FALLBACK;
            }
        }

        var boundary = PersonalityBoundaryPolicy.from(base);
        var resolvedContext = context.withAgentId(agentId);
        var deltas = config.personalityLearningEnabled()
                ? memoryQueryService.findPersonalityDeltas(context.scopeId(), agentId)
                    .stream()
                    .filter(boundary::accepts)
                    .toList()
                : java.util.List.<org.zexnocs.teanekoagent_old.memory.PersonalityDeltaRecord>of();
        var memories = config.memoryEnabled()
                ? memoryQueryService.findRelevant(resolvedContext)
                : java.util.List.<org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord>of();

        return new ResolvedAgentPersonality(
                agentId,
                source,
                base,
                boundary,
                deltas,
                memories,
                config,
                modelOptionsResolver.resolve(config));
    }

    /**
     * 解析实际使用的 agent ID。
     * <br>优先使用请求上下文中的 agent ID，其次使用配置中的 agent ID，最后使用文件配置默认 agent ID。
     *
     * @param contextAgentId 请求上下文中的 agent ID。
     * @param configAgentId  配置中的 agent ID。
     * @return 实际使用的 agent ID。
     */
    private String resolveAgentId(String contextAgentId, String configAgentId) {
        if (contextAgentId != null && !contextAgentId.isBlank()) {
            return contextAgentId.trim();
        }
        if (configAgentId != null && !configAgentId.isBlank()) {
            return configAgentId.trim();
        }
        return fileConfigService.getDefaultAgentId();
    }
}
