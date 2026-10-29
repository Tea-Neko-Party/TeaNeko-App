package org.zexnocs.teanekoagent.agent.summary;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentHandle;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentRunResult;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationWindow;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.concurrent.CompletionStage;

/**
 * 会话压缩端口，选择已生成结果与启动后台工作分开，前台不等待摘要模型。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentConversationSummaries {
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
     * @param window 仍须保留的完整轮次
     * @param summary 当前采用摘要，未启用时为空
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Selection(AgentConversationWindow window, @Nullable AgentSummarySnapshot summary) {}
}
