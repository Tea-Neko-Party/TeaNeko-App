package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/07：通过中立生命周期端口接入宿主，认知类型和业务校验仍归插件。
import org.zexnocs.teanekoagent.runtime.context.IAgentPromptContexts;
import org.zexnocs.teanekoagent.runtime.context.IAgentPromptContext;
import org.zexnocs.teanekoagent.component.AgentAssembly;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;

/**
 * 人格解析及当前身份复核端口，Agent 不依赖文件、数据库或补正存储实现。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public interface IAgentPersonalitySource extends IAgentPromptContexts {
    /**
     * 解析本轮基础人格及补正快照，必要的初始化或切换须等待提交。
     * @param scope 真实作用域
     * @param control 借用执行控制
     * @return 不含运行资源的人格快照
     */
    CompletionStage<AgentPersonalitySnapshot> resolve(AgentScope scope, ExecutionControl control);

    /**
     * 只读复核基础设定和绑定仍有效；新补正从下一轮生效，不覆盖本轮快照。
     * @param scope 原作用域
     * @param snapshot 原快照
     * @param control 当前使用或观察的借用控制
     * @return 原人格身份仍有效时完成
     */
    CompletionStage<Void> verify(AgentScope scope, AgentPersonalitySnapshot snapshot, ExecutionControl control);

    /** @param assembly 实际装配 @return 人格或记忆是否需要认知身份分区 */
    @Override
    default boolean supports(AgentAssembly assembly) {
        return assembly.has("personality") || assembly.has("memory");
    }

    /**
     * 独立记忆沿用无人格分区，不读取或创建人格存储。
     * @param assembly 本轮实际装配
     * @param scope 原真实作用域
     * @param control 借用本轮控制
     * @return 实际人格或显式无人格分区
     */
    @Override
    default CompletionStage<AgentPersonalitySnapshot> resolve(
            AgentAssembly assembly, AgentScope scope, ExecutionControl control) {
        control.checkActive();
        return assembly.has("personality") ? resolve(scope, control)
                : java.util.concurrent.CompletableFuture.completedStage(AgentPersonalitySnapshot.absent(scope));
    }

    /**
     * 将中立提示上下文交回人格的原身份复核。
     * @param scope 原作用域
     * @param context 宿主实际采用的上下文
     * @param control 借用执行控制
     * @return 原人格身份仍有效时完成
     */
    @Override
    default CompletionStage<Void> verify(AgentScope scope, IAgentPromptContext context, ExecutionControl control) {
        if (!(context instanceof AgentPersonalitySnapshot personality)) {
            throw new IllegalArgumentException("认知提示上下文类型不匹配。");
        }
        return verify(scope, personality, control);
    }

}
