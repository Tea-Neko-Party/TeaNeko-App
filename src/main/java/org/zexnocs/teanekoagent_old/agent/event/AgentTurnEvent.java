package org.zexnocs.teanekoagent_old.agent.event;

import org.zexnocs.teanekoagent_old.agent.AgentRuntimeService;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * Agent 单轮运行时事件。
 * <br>该事件是 Agent Runtime 的最外层事件。监听器可以在默认运行时处理前修改 {@link AgentTurnData}，也可以取消事件以跳过默认对话流程。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Event(value = AgentTurnEvent.KEY, namespace = AgentTurnEvent.NAMESPACE)
public class AgentTurnEvent extends AbstractEvent<AgentTurnData> {
    /**
     * 事件扫描注册 key。
     */
    public static final String KEY = "teaneko-agent-turn";

    /**
     * Agent 运行时事件处理阶段命名空间。
     */
    public static final String NAMESPACE = "teaneko-agent-runtime";

    /**
     * Agent 运行时服务，用于执行事件未取消时的默认处理逻辑。
     */
    private final AgentRuntimeService runtimeService;

    /**
     * 创建 Agent 单轮运行时事件。
     *
     * @param data           单轮运行时事件数据。
     * @param runtimeService Agent 运行时服务。
     */
    public AgentTurnEvent(AgentTurnData data, AgentRuntimeService runtimeService) {
        super(data);
        this.runtimeService = runtimeService;
    }

    /**
     * 在监听器处理完成后执行默认 Agent 运行时流程。
     */
    @Override
    public void _afterNotify() {
        runtimeService.__handleForEvent(getData());
    }
}
