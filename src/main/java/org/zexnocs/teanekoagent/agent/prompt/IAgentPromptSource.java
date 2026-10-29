package org.zexnocs.teanekoagent.agent.prompt;

import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 为单轮提供可等待的低信任参考资料；身份、引用和权限仍由宿主请求拥有。
 * 不负责历史提交、模型往返或发送；实现不得关闭借用控制和执行器。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentPromptSource {
    /**
     * 在业务域解析本轮资料，片段按优先级排列，输入不足时从末尾整项省略。
     * 每项须是独立完整证据，不能把完整性声明与其事实分开后任意省略。
     * 动态源通过 AgentPromptException 表达关闭、引用变化或不可用，不用空列表掩盖失败。
     * @param definition 当前定义
     * @param request 宿主原始请求与实际固定引用，不从历史生成授权
     * @param runtime 本轮运行快照
     * @param control 借用轮次控制，等待计入原总时限
     * @return 独立完整片段；空表示本轮不注入，失败不能伪装成已确认无资料
     */
    CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request,
            AgentRuntimeSnapshot runtime, ExecutionControl control);

    /**
     * 在每次模型、工具和交付前检查本轮实际使用资料是否仍有效，不刷新为另一个来源或空间。
     * @param request 原可信请求及固定引用
     * @param input 实际保留的输入投影，包含预算省略信息
     * @param control 借用控制
     * @return 当前资料仍可使用的完成信号；静态资料默认无额外检查
     */
    default CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        control.checkActive(); return CompletableFuture.completedStage(null);
    }

    /**
     * 只读复核旧资料能否参与当前重放；默认拒绝，静态检查不能隐式成为动态来源的重放授权。
     * 不重新检索、调用模型或执行工具，控制由观察拥有者创建并回收。
     * @param request 保留原身份和凭据、携带旧实际引用的固定请求
     * @param input 原交互已经使用的不可变布局
     * @param control 本次观察的借用控制，不是已结束原轮次的控制
     * @return 已明确支持且当前校验通过时为 true，否则明确不可重放
     */
    default CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        control.checkActive(); return CompletableFuture.completedStage(false);
    }

    /**
     * 保留没有资料适配器的明确组合方式，构造和调用不读取配置或存储。
     * @return 不注入资料的默认策略
     */
    static IAgentPromptSource empty() { return (definition, request, runtime, control) -> CompletableFuture.completedStage(List.of()); }
}
