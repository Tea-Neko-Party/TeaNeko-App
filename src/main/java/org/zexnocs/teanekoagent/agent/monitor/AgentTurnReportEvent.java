package org.zexnocs.teanekoagent.agent.monitor;

import org.zexnocs.teanekocore.event.AbstractEvent;

/**
 * 异步监控事件，只提供不可变统计，事件取消或替换不影响已结束的业务轮次。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentTurnReportEvent extends AbstractEvent<AgentTurnReport> {
    /**
     * 固定脱敏报告，不保留运行句柄。
     * @param report 只读计数
     */
    public AgentTurnReportEvent(AgentTurnReport report) { super(report); }
}
