package org.zexnocs.teanekoagent.runtime.prompt;

import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 在实际运行时取得已由容器管理的资料来源，静态目录发现不解析业务依赖。
 * <br>2026/11/07：完整转发读取、复核和重放合同。
 *
 * @param source 已管理来源的提供者
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record DeferredAgentPromptSource(Supplier<IAgentPromptSource> source) implements IAgentPromptSource {
    /** 将原请求及授权上下文完整转发给容器拥有的来源。 */
    @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request,
            AgentRuntimeSnapshot runtime, ExecutionControl control) {
        return source.get().resolve(definition, request, runtime, control);
    }
    /** 将原请求及授权上下文完整转发给容器拥有的来源。 */
    @Override public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        return source.get().verify(request, input, control);
    }
    /** 将原请求及授权上下文完整转发给容器拥有的来源。 */
    @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        return source.get().verifyReplay(request, input, control);
    }
}
