package org.zexnocs.teanekoagent.runtime;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import java.util.List;
import java.util.function.Supplier;

/**
 * 在使用时取得原工具来源，完整保留最终工具集合的依赖复核。
 * <br>2026/11/07：静态发现不初始化插件业务服务。
 *
 * @param source 已管理工具来源
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record DeferredAgentToolSource(Supplier<IAgentToolSource> source) implements IAgentToolSource {
    /** 将原请求及授权上下文完整转发给容器拥有的来源。 */
    @Override public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime) {
        return source.get().select(definition, request, runtime);
    }
    /** 将原请求及授权上下文完整转发给容器拥有的来源。 */
    @Override public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request,
            AgentRuntimeSnapshot runtime, AgentPromptInput input) {
        return source.get().select(definition, request, runtime, input);
    }
    /** 将原请求及授权上下文完整转发给容器拥有的来源。 */
    @Override public void verifySelection(AgentRunRequest request, AgentPromptInput input, List<LLMToolBinding> selected) {
        source.get().verifySelection(request, input, selected);
    }
}
