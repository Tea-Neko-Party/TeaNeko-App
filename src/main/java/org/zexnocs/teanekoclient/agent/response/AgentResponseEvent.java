package org.zexnocs.teanekoclient.agent.response;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.zexnocs.teanekoapp.response.ResponseEvent;
/**
 * 内部 Agent 响应事件，不回灌原生消息、命令或外部平台事件。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
public final class AgentResponseEvent extends ResponseEvent {
    /**
     * 绑定原 echo 及响应，不解释为入站消息。
     * @param data 实际响应信封
     */
    public AgentResponseEvent(AgentResponseData data) { super(data, AgentResponseData.class); }
}
