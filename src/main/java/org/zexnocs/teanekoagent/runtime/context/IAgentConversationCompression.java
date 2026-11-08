package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationWindow;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * 会话窗口的可替换压缩生命周期，选择已完成附件与后台生成分离。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentConversationCompression {
    /**
     * @param assembly 当前有效装配
     * @return 此装配是否参与压缩
     */
    boolean supports(AgentAssembly assembly);
    /**
     * @param stamp 本轮会话代次
     * @param reference 固定作用域引用
     * @param contextIdentity 当前必需提示身份
     * @param window 当前完整窗口
     * @param previous 已采用附件
     * @return 可原子发布的附件与剩余窗口，不执行模型
     */
    ISelection select(AgentConversationStamp stamp, IAgentContextReference reference, String contextIdentity,
            AgentConversationWindow window, @Nullable IAgentConversationAttachment previous);
    /**
     * @param handle 原实例
     * @param result 已提交成功结果
     * @param window 截断之前的成功窗口
     * @param base 已采用附件
     */
    void offer(AgentHandle handle, AgentRunResult result, AgentConversationWindow window,
            @Nullable IAgentConversationAttachment base);
    /**
     * @param request 原固定请求
     * @param attachment 实际采用的附件
     * @param control 借用当前控制
     * @return 原附件的业务资格及覆盖仍有效时完成
     */
    CompletionStage<Void> verify(AgentRunRequest request, IAgentConversationAttachment attachment, ExecutionControl control);
    /**
     * 压缩选择的原子数据视图。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    interface ISelection {
        /** @return 仍须保留的完整窗口 */
        AgentConversationWindow window();
        /** @return 本次实际采用的附件，未采用时为空 */
        @Nullable IAgentConversationAttachment attachment();
    }
}
