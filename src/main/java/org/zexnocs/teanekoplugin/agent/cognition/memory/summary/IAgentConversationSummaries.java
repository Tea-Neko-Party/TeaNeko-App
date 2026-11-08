package org.zexnocs.teanekoplugin.agent.cognition.memory.summary;

// 2026/11/07：通过中立生命周期端口接入宿主，认知类型和业务校验仍归插件。
import org.zexnocs.teanekoagent.runtime.context.IAgentConversationCompression;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoagent.runtime.context.IAgentConversationAttachment;
import org.zexnocs.teanekoagent.component.AgentAssembly;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationWindow;
import org.zexnocs.teanekoplugin.agent.cognition.memory.AgentSummarySnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationStamp;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.concurrent.CompletionStage;

/**
 * 会话压缩端口，选择已生成结果与启动后台工作分开，前台不等待摘要模型。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：组件数据引用迁入 component，保持原业务流程。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public interface IAgentConversationSummaries extends IAgentConversationCompression {
    /**
     * 在邮箱锁外选择同代次、同基础且覆盖指纹匹配的已完成摘要，不执行模型。
     * @param stamp 当前代次
     * @param reference 当前固定引用
     * @param personalityIdentity 当前人格身份
     * @param window 当前完整窗口
     * @param previous 邮箱已接受的摘要
     * @return 可一起提交的摘要和剩余完整窗口
     */
    Selection select(AgentConversationStamp stamp, MemoryNamespaceReference reference, String personalityIdentity,
                     AgentConversationWindow window, @Nullable AgentSummarySnapshot previous);
    /**
     * 在成功轮次提交后尝试启动有界后台摘要；拒绝或失败不得影响本轮结果。
     * @param handle 实例拥有者
     * @param result 已提交成功结果
     * @param window 截断前的完整成功窗口
     * @param base 邮箱已接受的旧摘要
     */
    void offer(AgentHandle handle, AgentRunResult result, AgentConversationWindow window, @Nullable AgentSummarySnapshot base);
    /**
     * 在模型、工具、最终交付和缓存重放前检查原摘要，不能刷新成新摘要。
     * @param request 原固定请求
     * @param summary 实际采用的摘要
     * @param control 当前使用或观察的借用控制
     * @return 原边界仍有效时完成
     */
    CompletionStage<Void> verify(AgentRunRequest request, AgentSummarySnapshot summary, ExecutionControl control);
    /**
     * 一次纯数据选择，摘要与被压缩窗口保持对应。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param window 仍须保留的完整轮次
     * @param summary 当前采用摘要，未启用时为空
     *
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Selection(AgentConversationWindow window, @Nullable AgentSummarySnapshot summary)
            implements IAgentConversationCompression.ISelection {
        /** @return 供宿主固定采用的原摘要 */
        @Override
        public @Nullable AgentSummarySnapshot attachment() {
            return summary;
        }
    }

    /** @param assembly 实际装配 @return 只有记忆组件参与会话摘要 */
    @Override
    default boolean supports(AgentAssembly assembly) {
        return assembly.has("memory");
    }

    /**
     * 中立压缩入口仍使用原认知引用、覆盖和基础身份校验。
     * @param stamp 原会话代次
     * @param reference 原引用
     * @param identity 原提示身份
     * @param window 原完整窗口
     * @param previous 已采用附件
     * @return 原摘要及剩余窗口
     */
    @Override
    default Selection select(AgentConversationStamp stamp, IAgentContextReference reference, String identity,
            AgentConversationWindow window, @Nullable IAgentConversationAttachment previous) {
        if (!(reference instanceof MemoryNamespaceReference memory)
                || previous != null && !(previous instanceof AgentSummarySnapshot)) {
            throw new IllegalArgumentException("认知压缩上下文类型不匹配。");
        }
        return select(stamp, memory, identity, window, (AgentSummarySnapshot) previous);
    }

    /**
     * 将宿主成功窗口交回原摘要策略。
     * @param handle 原实例
     * @param result 已提交结果
     * @param window 完整成功窗口
     * @param base 已采用附件
     */
    @Override
    default void offer(AgentHandle handle, AgentRunResult result, AgentConversationWindow window,
            @Nullable IAgentConversationAttachment base) {
        if (base != null && !(base instanceof AgentSummarySnapshot)) {
            throw new IllegalArgumentException("认知摘要基础类型不匹配。");
        }
        offer(handle, result, window, (AgentSummarySnapshot) base);
    }

    /**
     * 复核已经采用的原摘要，不把失效附件替换为新摘要。
     * @param request 原请求
     * @param attachment 原附件
     * @param control 借用执行控制
     * @return 原摘要仍有效时完成
     */
    @Override
    default CompletionStage<Void> verify(AgentRunRequest request, IAgentConversationAttachment attachment,
            ExecutionControl control) {
        if (!(attachment instanceof AgentSummarySnapshot summary)) {
            throw new IllegalArgumentException("认知摘要类型不匹配。");
        }
        return verify(request, summary, control);
    }

}
