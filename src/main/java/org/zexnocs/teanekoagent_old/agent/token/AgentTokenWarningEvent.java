package org.zexnocs.teanekoagent_old.agent.token;

import org.zexnocs.teanekoagent_old.agent.event.AgentTurnEvent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * Agent token 告警事件。
 * <br>该事件在单轮对话完成后、token 监控器写入 warn 日志前触发，监听器可以读取或处理告警数据。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
@Event(value = AgentTokenWarningEvent.KEY, namespace = AgentTurnEvent.NAMESPACE)
public class AgentTokenWarningEvent extends AbstractEvent<AgentTokenWarningData> {
    /**
     * 事件扫描注册 key。
     */
    public static final String KEY = "teaneko-agent-token-warning";

    /**
     * 创建 Agent token 告警事件。
     *
     * @param data token 告警事件数据。
     */
    public AgentTokenWarningEvent(AgentTokenWarningData data) {
        super(data);
    }
}
