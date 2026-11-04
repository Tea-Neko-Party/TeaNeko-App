package org.zexnocs.teanekoagent.component.memory;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder;
import org.zexnocs.teanekocore.framework.composition.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 在最终交付前提交成功回答的语义历史，不把表达占位符或失败候选保存成正式答案。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Component
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
                AgentComponents.INTERACTION, AgentComponents.PROMPT_INPUT, AgentComponents.MEMORY_REFERENCE),
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
                .withMemoryReference(snapshot.require(AgentComponents.MEMORY_REFERENCE));
        return history.getObject().record(request, snapshot.require(AgentComponents.PROMPT_INPUT), answer, context.control())
                .thenApply(ignored -> SystemResult.continueWith(ComponentPatch.empty()));
    }
}
