package org.zexnocs.teanekoagent.response;
import org.zexnocs.teanekoapp.response.ResponseEvent;
/**
 * 内部 Agent 响应事件，不回灌原生消息、命令或外部平台事件。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
public final class AgentResponseEvent extends ResponseEvent {
    /**
     * 绑定原 echo 及响应，不解释为入站消息。
     * @param data 实际响应信封
     */
    public AgentResponseEvent(AgentResponseData data) { super(data, AgentResponseData.class); }
}
