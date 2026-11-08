package org.zexnocs.teanekoagent.runtime.delivery;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.concurrent.CompletionStage;

/**
 * 可选回复编译端口，宿主仅提供已授权轮次并在使用前复核固定计划。
 * <br>2026/11/07：业务协议识别、资源解析和插件代次由实现负责。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentReplyCompiler {
    /**
     * @param assembly 当前实际装配
     * @return 本轮是否采用该编译器
     */
    boolean supports(org.zexnocs.teanekoagent.component.AgentAssembly assembly);
    /**
     * @param request 原请求
     * @param input 实际模型输入
     * @param interaction 实际模型结果
     * @param control 借用的本轮控制
     * @return 完整编译计划；本轮未采用扩展时返回空值
     */
    CompletionStage<? extends @Nullable IAgentReplyPlan> compile(AgentRunRequest request, AgentPromptInput input,
            LLMInteractionResult interaction, ExecutionControl control);

    /**
     * 2026/11/08：从组件执行结果取得本提供者已编译的计划，数据键由实现拥有。
     * @param snapshot 实际组件阶段完成后的只读快照
     * @return 本轮编译计划；未采用当前提供者时为空
     */
    @Nullable IAgentReplyPlan prepared(org.zexnocs.teanekocore.framework.composition.EntitySnapshot snapshot);

    /**
     * @param plan 原编译计划
     * @throws IllegalStateException 当贡献资格或计划绑定失效时
     */
    void verify(IAgentReplyPlan plan);

    /**
     * @param plan 已复核的原计划
     * @return 固定正式答案
     */
    default ILLMAssistantMessage answer(IAgentReplyPlan plan) {
        return plan.answer();
    }
}
