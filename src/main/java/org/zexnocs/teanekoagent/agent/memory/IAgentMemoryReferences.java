package org.zexnocs.teanekoagent.agent.memory;

import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import java.util.concurrent.CompletionStage;

/**
 * 为独立轮次提供当前持久化空间引用及异步复核，不把存储资源或权限对象交给 LLM。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentMemoryReferences {
    /**
     * 在已通过 Agent 门控后解析当前引用，必要时按需登记默认共享空间。
     * @param scope 真实作用域
     * @return 已固定且定位一致的引用；未启用或存储不可用时异常结束
     */
    CompletionStage<MemoryNamespaceReference> resolve(AgentScope scope);

    /**
     * 只读复核原始引用，缺失或版本变化时失败，不创建空间或刷新本轮绑定。
     * @param expected 已固定的原始引用
     * @return 当前引用仍一致的完成信号，失败不得继续模型、工具或结果交付
     */
    CompletionStage<Void> verify(MemoryNamespaceReference expected);
}
