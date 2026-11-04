package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
/**
 * 内容解析时借用的可信上下文，仅在调用链传递，不进入组件、计划或持久化。
 * <br>2026/11/03：同时提供当前实际交互的工具回执，拒绝历史目录授权。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：提供明确的借用资源 getter，保留旧访问器和原拥有者的关闭责任。
 * @param request 原请求及真实 client/scope
 * @param input 本轮实际输入和来源
 * @param control 借用控制，扩展不得关闭
 * @param tools 本次交互真实工具结果
 * @param drafts 完整草稿，用于资源数量及重复约束
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@JsonIgnoreType
public record ReplyCompileContext(AgentRunRequest request, AgentPromptInput input, ExecutionControl control,
        java.util.List<org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationResult> tools,java.util.List<ReplyPlans.Draft> drafts) {
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

    /**
     * 2026/11/05：内容解析只借用轮次控制，不能因一次访问而关闭整个异步流程。
     * @return 原拥有者管理的执行控制，不转移关闭责任
     */
    public ExecutionControl getControl() {
        return control;
    }
}
