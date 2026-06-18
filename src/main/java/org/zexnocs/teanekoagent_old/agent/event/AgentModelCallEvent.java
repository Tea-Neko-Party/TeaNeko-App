package org.zexnocs.teanekoagent_old.agent.event;

import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelService;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * Agent 模型调用事件。
 * <br>该事件在 Agent Runtime 调用 LLM 模型前触发。监听器可以修改 Prompt、替换结果或取消默认模型调用。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Event(value = AgentModelCallEvent.KEY, namespace = AgentTurnEvent.NAMESPACE)
public class AgentModelCallEvent extends AbstractEvent<AgentModelCallData> {
    /**
     * 事件扫描注册 key。
     */
    public static final String KEY = "teaneko-agent-model-call";

    /**
     * LLM 模型调用服务，用于执行事件未取消时的默认模型调用。
     */
    private final LLMModelService llmModelService;

    /**
     * 创建 Agent 模型调用事件。
     *
     * @param data            模型调用事件数据。
     * @param llmModelService LLM 模型调用服务。
     */
    public AgentModelCallEvent(AgentModelCallData data, LLMModelService llmModelService) {
        super(data);
        this.llmModelService = llmModelService;
    }

    /**
     * 在监听器处理完成后执行默认模型调用，并把结果写回事件数据。
     */
    @Override
    public void _afterNotify() {
        getData().setResult(llmModelService.call(getData().getPrompt()).finish().join());
    }
}
