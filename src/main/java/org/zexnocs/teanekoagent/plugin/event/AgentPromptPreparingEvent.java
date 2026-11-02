package org.zexnocs.teanekoagent.plugin.event;

import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;
import java.util.List;

/**
 * 资料解析结束、预算裁剪之前的可修改事件；业务项始终为低信任参考资料。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public final class AgentPromptPreparingEvent extends AgentDecisionEvent<AgentPromptFragment> {
    /**
     * 创建本轮资料草稿。
     * @param context 可信定位
     * @param fragments 完整资料候选
     */
    public AgentPromptPreparingEvent(AgentEventContext context, List<AgentPromptFragment> fragments) { super(context, fragments); }
}
