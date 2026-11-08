package org.zexnocs.teanekoplugin.agent.cognition;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoagent.runtime.context.IAgentConversationAttachment;
import org.zexnocs.teanekoagent.runtime.context.IAgentPromptContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.AgentSummarySnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;

/**
 * 认知插件读取宿主中立上下文的类型边界，不授予访问权限或刷新已固定数据。
 * <br>2026/11/07：宿主只携带中立合同，实际认知快照在插件内显式转换。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class CognitionContext {
    /** 禁止创建无状态上下文转换器。 */
    private CognitionContext() {}

    /**
     * @param value 宿主固定引用
     * @return 原认知引用，缺省保持为空
     * @throws IllegalArgumentException 当其他提供者的引用被传入认知业务时
     */
    public static @Nullable MemoryNamespaceReference reference(@Nullable IAgentContextReference value) {
        if (value == null) {
            return null;
        }
        if (value instanceof MemoryNamespaceReference reference) {
            return reference;
        }
        throw new IllegalArgumentException("认知上下文引用类型不匹配。");
    }

    /**
     * @param value 实际采用的必需提示上下文
     * @return 原人格快照，缺省保持为空
     * @throws IllegalArgumentException 当其他提供者的上下文被传入人格业务时
     */
    public static @Nullable AgentPersonalitySnapshot personality(@Nullable IAgentPromptContext value) {
        if (value == null) {
            return null;
        }
        if (value instanceof AgentPersonalitySnapshot personality) {
            return personality;
        }
        throw new IllegalArgumentException("认知提示上下文类型不匹配。");
    }

    /**
     * @param value 实际采用的会话附件
     * @return 原摘要快照，缺省保持为空
     * @throws IllegalArgumentException 当其他提供者的附件被传入摘要业务时
     */
    public static @Nullable AgentSummarySnapshot summary(@Nullable IAgentConversationAttachment value) {
        if (value == null) {
            return null;
        }
        if (value instanceof AgentSummarySnapshot summary) {
            return summary;
        }
        throw new IllegalArgumentException("认知会话附件类型不匹配。");
    }
}
