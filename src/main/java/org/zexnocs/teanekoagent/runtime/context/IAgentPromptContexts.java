package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;

/**
 * 必需提示上下文的解析与复核端口，具体配置和业务身份不进入宿主底座。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentPromptContexts {
    /**
     * @param assembly 本轮实际组件装配
     * @return 是否需要本提供者的上下文
     */
    boolean supports(AgentAssembly assembly);
    /**
     * @param scope 可信作用域
     * @param control 借用本轮执行控制
     * @return 固定且可复核的必需上下文
     */
    CompletionStage<? extends IAgentPromptContext> resolve(AgentScope scope, ExecutionControl control);
    /**
     * 按实际装配解析上下文，具体能力缺省行为由提供者决定。
     * @param assembly 本轮实际组件装配
     * @param scope 可信作用域
     * @param control 借用本轮控制
     * @return 与本轮装配一致的固定上下文
     */
    default CompletionStage<? extends IAgentPromptContext> resolve(
            AgentAssembly assembly, AgentScope scope, ExecutionControl control) {
        return resolve(scope, control);
    }
    /**
     * @param scope 原作用域
     * @param context 本轮固定上下文，不允许替换
     * @param control 借用执行或观察控制
     * @return 当前身份及资格仍成立时完成
     */
    CompletionStage<Void> verify(AgentScope scope, IAgentPromptContext context, ExecutionControl control);
}
