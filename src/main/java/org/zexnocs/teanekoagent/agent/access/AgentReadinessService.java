package org.zexnocs.teanekoagent.agent.access;

import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 调用时检查运行设置、路由/能力与凭据，失败只关闭受影响 Agent 的准入，不阻断 App。
 * 不发送探测请求，检查成功也不表示远端模型或数据库已通过实机联调。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentReadinessService implements IAgentReadiness {
    private final IAgentRuntimeConfigPort config;
    private final ILLMModelService models;
    private final ICredentialResolver credentials;
    private final ILogger logger;

    /**
     * 仅保存依赖，构造 Bean 时不访问本地配置、凭据或数据库。
     * @param config Agent 本地运行设置端口
     * @param models 模型预检端口
     * @param credentials Spring 凭据边界
     * @param logger 记录固定中文原因的日志端口
     */
    public AgentReadinessService(IAgentRuntimeConfigPort config, ILLMModelService models,
                                 ICredentialResolver credentials, ILogger logger) {
        this.config = Objects.requireNonNull(config, "Agent 配置端口不能为空。");
        this.models = Objects.requireNonNull(models, "模型服务不能为空。");
        this.credentials = Objects.requireNonNull(credentials, "凭据端口不能为空。");
        this.logger = Objects.requireNonNull(logger, "日志端口不能为空。");
    }

    /**
     * 分阶段隔离配置错误，只在全部检查成功后形成不含凭据的快照。
     * @param definition 已固定定义
     * @return 明确就绪状态，外部配置异常转换为固定诊断
     */
    @Override public AgentReadiness inspect(AgentDefinition definition) {
        Objects.requireNonNull(definition, "Agent 定义不能为空。");
        final AgentRuntimeConfig settings;
        try { settings = Objects.requireNonNull(config.read()); }
        catch (RuntimeException failure) { return unavailable(AgentReadiness.Status.LOCAL_CONFIG_UNAVAILABLE); }
        if (!settings.enabled()) { return new AgentReadiness(AgentReadiness.Status.LOCAL_DISABLED, Optional.empty()); }
        final LLMModelOptions options;
        final LLMModelOptionsSnapshot snapshot;
        final ModelCapabilities capabilities;
        try {
            snapshot = new LLMModelOptionsSnapshot(Objects.requireNonNull(models.resolveOptions(
                    new LLMPrompt(List.of(), definition.modelOptions().toOptions()))));
            options = snapshot.toOptions();
            if (options.getProvider().isBlank() || options.getModel().isBlank() || options.getMaxTokens() < 1) {
                return unavailable(AgentReadiness.Status.MODEL_UNAVAILABLE);
            }
            capabilities = Objects.requireNonNull(models.getModel(options.getProvider()).getCapabilities(options.getModel()));
        } catch (RuntimeException failure) { return unavailable(AgentReadiness.Status.MODEL_UNAVAILABLE); }
        try { Objects.requireNonNull(credentials.require(options.getProvider())); }
        catch (RuntimeException failure) { return unavailable(AgentReadiness.Status.CREDENTIALS_UNAVAILABLE); }
        return new AgentReadiness(AgentReadiness.Status.READY,
                Optional.of(new AgentRuntimeSnapshot(snapshot, capabilities, settings.interaction())));
    }

    /**
     * 仅记录预定义原因，不向日志系统转交可能包含密钥的原始异常和参数。
     * 日志端口故障不应使配置错误逃逸为应用启动异常。
     * @param status 已确定的失败阶段
     * @return 无运行快照的就绪结果
     */
    private AgentReadiness unavailable(AgentReadiness.Status status) {
        try { logger.error(AgentReadinessService.class.getName(), status.description()); }
        catch (RuntimeException ignored) { /* 诊断失败不改变 Agent 的不可用结论。 */ }
        return new AgentReadiness(status, Optional.empty());
    }
}
