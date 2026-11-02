package org.zexnocs.teanekoagent.plugin.event;

import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import java.util.List;

/**
 * 工具候选固定前的筛选事件，允许移除或重排；增加工具应通过能力包贡献。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public final class AgentToolsSelectingEvent extends AgentDecisionEvent<LLMToolBinding> {
    /**
     * 创建本轮工具候选草稿。
     * @param context 可信定位
     * @param tools 原工具候选
     */
    public AgentToolsSelectingEvent(AgentEventContext context, List<LLMToolBinding> tools) { super(context, tools); }
}
