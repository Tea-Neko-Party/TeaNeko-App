package org.zexnocs.teanekoagent.agent.history;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;

/**
 * 记录本轮原文和可交付答案的业务端口，不保存临时 Prompt、推理或工具诊断。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentHistoryRecorder {
    /**
     * 等待历史提交，控制只借用；缺少真实原文或实际持久化上下文时不猜测来源。
     * @param request 宿主固定请求
     * @param input 实际人格及输入投影
     * @param answer 成功答案；null 表示仅记录当前用户原文
     * @param control 本阶段借用控制
     * @return 提交完成信号
     */
    CompletionStage<Void> record(AgentRunRequest request, AgentPromptInput input, @Nullable ILLMAssistantMessage answer, ExecutionControl control);
}
