package org.zexnocs.teanekoagent.llm.framework.tool;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;

import java.util.Objects;
import java.util.UUID;

/**
 * 仅宿主构造的本轮绑定；复用 Core 类型化组件，不定义 Agent/平台权限语义。
 * 组件值须为不可变数据，不能放凭据、Bean 或 Repository；此对象不进入 Prompt/快照。
 * control 由调用方拥有，工具不得 close；阶段控制可由交互层替换为共享预算的子控制。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：提供明确的借用资源 getter，保留旧访问器和原拥有者的关闭责任。
 * <br>2026/11/06：显式传递汇总归属，不使用线程变量或全局请求缓存。
 * @param requestId 由宿主生成的请求标识
 * @param bindings 宿主固定的不可变业务绑定或本轮工具绑定集合
 * @param control 借用的取消、截止时间和预算控制
 * @param externallyObserved 宿主将在完整轮次结束时统一报告
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@JsonIgnoreType
public record ToolInvocationContext(UUID requestId, EntitySnapshot bindings, ExecutionControl control,
                                    boolean externallyObserved) {
    /**
     * 独立 LLM 调用默认由交互层汇总。
     * @param requestId 请求编号
     * @param bindings 可信绑定
     * @param control 借用控制
     */
    public ToolInvocationContext(UUID requestId, EntitySnapshot bindings, ExecutionControl control) {
        this(requestId, bindings, control, false);
    }
    /**
     * 校验由宿主提供的请求标识、不可变绑定和借用控制；模型参数不参与上下文构造。
     *
     * @param requestId 由宿主生成的请求标识
     * @param bindings 宿主固定的不可变业务绑定或本轮工具绑定集合
     * @param control 借用的取消、截止时间和预算控制
     * @param externallyObserved 是否由外层统一报告
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public ToolInvocationContext {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(control, "control");
    }

    /**
     * 为独立阶段替换借用控制，继续保留本轮请求标识与可信绑定，不复制或重置预算。
     *
     * @param stageControl 本阶段借用的控制，通常为共享父预算的子控制
     * @return 使用新阶段控制且保留相同请求标识与绑定的上下文
     */
    public ToolInvocationContext withControl(ExecutionControl stageControl) {
        return new ToolInvocationContext(requestId, bindings, stageControl, externallyObserved);
    }

    /**
     * 2026/11/05：显式 getter 只借用宿主资源，不创建或转移执行控制的关闭责任。
     * @return 由原轮次或阶段拥有的同一执行控制，工具不得关闭
     */
    public ExecutionControl getControl() {
        return control;
    }

    /**
     * 仅输出请求标识和脱敏标记，不序列化身份、绑定内容或执行资源。
     *
     * @return 按本方法契约生成的文本或标识
     */
    @Override
    @JsonValue
    @NonNull
    public String toString() {
        return "ToolInvocationContext[requestId=" + requestId + ", bindings=redacted]";
    }
}
