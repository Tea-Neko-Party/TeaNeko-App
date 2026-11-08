package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * 受控历史交接端口，具体持久化、隐私和业务权限由实现负责。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentTranscriptRecorder {
    /**
     * @param assembly 本轮实际装配
     * @return 是否参与此装配的历史交接
     */
    boolean supports(AgentAssembly assembly);
    /**
     * @param request 可信原请求
     * @param input 实际采用的输入投影
     * @param answer 可交付答案；为空时仅交接原文
     * @param control 借用执行控制
     * @return 真实持久提交结束信号
     */
    CompletionStage<Void> record(AgentRunRequest request, AgentPromptInput input,
            @Nullable ILLMAssistantMessage answer, ExecutionControl control);
}
