package org.zexnocs.teanekoagent.history;

import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import java.util.Objects;

/**
 * 当前历史访问的持久化边界；内存窗口过期或进程重启不会修改它。
 * @param reference 当前完整空间引用，包含 client/scope、绑定版本和 generation
 * @param personality 当前实际人格，用于身份及到期复核，不将人格正文写入历史
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record HistoryContext(MemoryNamespaceReference reference, AgentPersonalitySnapshot personality) {
    /**
     * 要求真实空间及人格，不接受无保护模式的历史访问。
     * @param reference 原空间引用
     * @param personality 原人格快照
     */
    public HistoryContext {
        Objects.requireNonNull(reference, "历史空间引用不能为空。"); Objects.requireNonNull(personality, "历史人格不能为空。");
    }
}
