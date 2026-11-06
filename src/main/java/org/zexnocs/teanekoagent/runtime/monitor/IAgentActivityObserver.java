package org.zexnocs.teanekoagent.runtime.monitor;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;

/**
 * 运行域的终态汇总观察，观测实现自行接入配置及日志，不参与业务成功判定。
 * <br>2026/11/06：隔离运行域与文件配置实现。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.5-l
 */
public interface IAgentActivityObserver {
    /**
     * 2026/11/06：只在 Agent 结束后通知一次，不把阶段正文存入时间线。
     * @param report 本轮计数和终态
     * @param result 本轮临时详细结果，旧调用方可不提供
     */
    void finished(AgentTurnReport report, @Nullable AgentRunResult result);
}
