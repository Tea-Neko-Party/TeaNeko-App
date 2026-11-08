package org.zexnocs.teanekoplugin.agent.cognition.memory;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.IEntitySystem;
import org.zexnocs.teanekocore.framework.composition.SystemContext;
import org.zexnocs.teanekocore.framework.composition.SystemDescriptor;
import org.zexnocs.teanekocore.framework.composition.SystemResult;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentHistoryRecorder;
import org.zexnocs.teanekocore.framework.composition.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 在最终交付前提交成功回答的语义历史，不把表达占位符或失败候选保存成正式答案。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@Component
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class MemoryOutputSystem implements IEntitySystem {
    private final ObjectProvider<IAgentHistoryRecorder> history;

    /** @param history 原持久历史服务 */
    public MemoryOutputSystem(ObjectProvider<IAgentHistoryRecorder> history) {
        this.history = history;
    }

    /** @return 只读已完成交互的持久化系统声明 */
    @Override
    public SystemDescriptor descriptor() {
        return new SystemDescriptor("agent.component.memory.commit", Set.of(AgentComponents.REQUEST,
                AgentComponents.INTERACTION, AgentComponents.PROMPT_INPUT, AgentComponents.CONTEXT_REFERENCE),
                Set.of(), Set.of());
    }

    /**
     * @param context 已完成交互与当前控制
     * @return 实际历史提交的完成信号
     */
    @Override
    public CompletionStage<SystemResult> execute(SystemContext context) {
        var snapshot = context.snapshot();
        var interaction = snapshot.require(AgentComponents.INTERACTION);
        if (interaction.status() != LLMInteractionResult.Status.SUCCESS) {
            return CompletableFuture.completedStage(SystemResult.continueWith(ComponentPatch.empty()));
        }
        var transcript = snapshot.find(AgentComponents.PROMPT_TRANSCRIPT).orElse(null);
        var answer = transcript != null && transcript.replyPlan() != null ? transcript.projectedAnswer()
                : interaction.finalCandidate().orElseThrow();
        var request = snapshot.require(AgentComponents.REQUEST)
                .withMemoryReference(snapshot.require(AgentComponents.CONTEXT_REFERENCE));
        return history.getObject().record(request, snapshot.require(AgentComponents.PROMPT_INPUT), answer, context.control())
                .thenApply(ignored -> SystemResult.continueWith(ComponentPatch.empty()));
    }
}
