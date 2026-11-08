package org.zexnocs.teanekoagent.component.tool;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;

import java.util.Objects;

/**
 * 注解工具的宿主上下文；不进入模型 schema、组件或序列化，执行控制只借用不关闭。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：组件数据引用迁入 component，保持原业务流程。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * @param definition 当前实例定义
 * @param request 当前固定请求
 * @param runtime 可选运行快照，独立宿主可以不提供
 * @param input 可选实际 Prompt 布局
 * @param call 实际执行时的原调用，选择阶段为空
 * @param invocation 实际执行控制和组件，选择阶段为空
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@JsonIgnoreType
public record TeaNekoAgentToolContext(AgentDefinition definition, AgentRunRequest request, @Nullable AgentRuntimeSnapshot runtime,
                                     @Nullable AgentPromptInput input, @Nullable ILLMToolCall call, @Nullable ToolInvocationContext invocation) {
    /**
     * 固定宿主身份，禁止缺少真实请求的上下文。
     * @param definition 当前定义
     * @param request 当前请求
     * @param runtime 可选运行快照
     * @param input 可选布局
     * @param call 可选调用
     * @param invocation 可选执行上下文
     */
    public TeaNekoAgentToolContext { Objects.requireNonNull(definition); Objects.requireNonNull(request); }

    /**
     * 只在执行阶段借用真实控制，核对请求及发起者，不能由工具 JSON 形成。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @return 当前实际工具执行上下文
     * @throws IllegalStateException 当处于选择阶段或交接不一致时
     */
    public ToolInvocationContext requireInvocation() {
        if (invocation == null || !invocation.requestId().equals(request.requestId())
                || !invocation.bindings().require(AgentComponents.ACTOR).equals(request.actor())) {
            throw new IllegalStateException("注解工具缺少匹配的真实执行上下文。");
        }
        invocation.getControl().checkActive(); return invocation;
    }

    /**
     * 取得原工具调用 ID 等数据，副作用幂等不能生成第二份随机调用。
     * @return 原模型调用
     * @throws IllegalStateException 当不在实际执行阶段时
     */
    public ILLMToolCall requireCall() {
        if (call == null) { throw new IllegalStateException("选择阶段没有实际工具调用。"); }
        return call;
    }

    /**
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @return 不包含原文、身份和控制资源的固定诊断文本
     */
    @Override
    @NonNull
    public String toString() { return "TeaNekoAgentToolContext[已隐藏运行上下文]"; }
}
