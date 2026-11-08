package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import java.util.function.BooleanSupplier;

/**
 * 成功会话提交后的可选后台观察，不能改变已确认的前台交付结果。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentConversationObserver {
    /**
     * @param assembly 本轮实际装配
     * @return 是否接受此装配的成功观察
     */
    boolean supports(AgentAssembly assembly);
    /**
     * @param handle 原实例
     * @param request 原始可信请求
     * @param result 已提交结果
     * @param current 原会话代次是否仍然有效
     */
    void offer(AgentHandle handle, AgentRunRequest request, AgentRunResult result, BooleanSupplier current);
}
