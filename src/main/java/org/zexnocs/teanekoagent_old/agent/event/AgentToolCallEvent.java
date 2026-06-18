package org.zexnocs.teanekoagent_old.agent.event;

import org.zexnocs.teanekoagent_old.tool.AgentToolRegistryService;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * Agent 工具调用事件。
 * <br>该事件在 Agent Runtime 执行模型请求的工具调用前触发。监听器可以改写工具调用、替换工具结果或记录工具审计信息。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Event(value = AgentToolCallEvent.KEY, namespace = AgentTurnEvent.NAMESPACE)
public class AgentToolCallEvent extends AbstractEvent<AgentToolCallData> {
    /**
     * 事件扫描注册 key。
     */
    public static final String KEY = "teaneko-agent-tool-call";

    /**
     * Agent 工具注册表服务，用于执行事件未取消时的默认工具调用。
     */
    private final AgentToolRegistryService toolRegistryService;

    /**
     * 创建 Agent 工具调用事件。
     *
     * @param data                工具调用事件数据。
     * @param toolRegistryService Agent 工具注册表服务。
     */
    public AgentToolCallEvent(AgentToolCallData data, AgentToolRegistryService toolRegistryService) {
        super(data);
        this.toolRegistryService = toolRegistryService;
    }

    /**
     * 在监听器处理完成后执行默认工具调用，并把成功结果或异常信息写回事件数据。
     */
    @Override
    public void _afterNotify() {
        try {
            getData().setResult(toolRegistryService.call(getData().getToolCall()));
        } catch (Exception exception) {
            getData().setException(exception);
            getData().setResult("Tool execution failed: " + exception.getMessage());
        }
    }
}
