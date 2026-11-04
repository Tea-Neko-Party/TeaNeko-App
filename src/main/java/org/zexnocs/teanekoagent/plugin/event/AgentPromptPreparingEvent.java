package org.zexnocs.teanekoagent.plugin.event;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import java.util.List;

/**
 * 资料解析结束、预算裁剪之前的可修改事件；业务项始终为低信任参考资料。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
 */
public final class AgentPromptPreparingEvent extends AgentDecisionEvent<AgentPromptFragment> {
    /**
     * 创建本轮资料草稿。
     * @param context 可信定位
     * @param fragments 完整资料候选
     */
    public AgentPromptPreparingEvent(AgentEventContext context, List<AgentPromptFragment> fragments) { super(context, fragments); }
}
