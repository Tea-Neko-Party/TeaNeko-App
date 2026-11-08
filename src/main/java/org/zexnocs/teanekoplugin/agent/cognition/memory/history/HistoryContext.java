package org.zexnocs.teanekoplugin.agent.cognition.memory.history;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;

import java.util.Objects;

/**
 * 当前历史访问的持久化边界；内存窗口过期或进程重启不会修改它。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param reference 当前完整空间引用，包含 client/scope、绑定版本和 generation
 * @param personality 当前实际人格，用于身份及到期复核，不将人格正文写入历史
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record HistoryContext(MemoryNamespaceReference reference, AgentPersonalitySnapshot personality) implements org.zexnocs.teanekoagent.runtime.context.IAgentCapturedContext {
    /**
     * 要求真实空间及人格，不接受无保护模式的历史访问。
     * @param reference 原空间引用
     * @param personality 原人格快照
     */
    public HistoryContext {
        Objects.requireNonNull(reference, "历史空间引用不能为空。"); Objects.requireNonNull(personality, "历史人格不能为空。");
    }
}
