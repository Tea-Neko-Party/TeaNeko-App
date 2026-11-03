package org.zexnocs.teanekoagent.expression;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
/**
 * 内容解析时借用的可信上下文，仅在调用链传递，不进入组件、计划或持久化。
 * <br>2026/11/03：同时提供当前实际交互的工具回执，拒绝历史目录授权。
 * @param request 原请求及真实 client/scope
 * @param input 本轮实际输入和来源
 * @param control 借用控制，扩展不得关闭
 * @param tools 本次交互真实工具结果
 * @param drafts 完整草稿，用于资源数量及重复约束
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@JsonIgnoreType
public record ReplyCompileContext(AgentRunRequest request, AgentPromptInput input, ExecutionControl control,
        java.util.List<org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult> tools,java.util.List<ReplyPlans.Draft> drafts) {
    /**
     * 2026/11/03：固定本次回执集合。
     * @param request 原请求
     * @param input 原布局
     * @param control 借用控制
     * @param tools 实际回执
     * @param drafts 原全部草稿
     */
    public ReplyCompileContext {tools=java.util.List.copyOf(tools);drafts=java.util.List.copyOf(drafts);}
    /**
     * 2026/11/03：保留不使用工具资源的原构造。
     * @param request 原请求
     * @param input 原布局
     * @param control 借用控制
     */
    public ReplyCompileContext(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){this(request,input,control,java.util.List.of(),java.util.List.of());}
}
