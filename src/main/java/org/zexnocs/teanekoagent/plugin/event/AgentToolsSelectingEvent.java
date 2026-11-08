package org.zexnocs.teanekoagent.plugin.event;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import java.util.List;

/**
 * 工具候选固定前的筛选事件，允许移除或重排；增加工具应通过能力包贡献。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
 */
public final class AgentToolsSelectingEvent extends AgentDecisionEvent<LLMToolBinding> {
    /**
     * 创建本轮工具候选草稿。
     * @param context 可信定位
     * @param tools 原工具候选
     */
    public AgentToolsSelectingEvent(AgentEventContext context, List<LLMToolBinding> tools) { super(context, tools); }
}
