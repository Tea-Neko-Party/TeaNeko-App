package org.zexnocs.teanekoagent.llm.framework.tool;

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
 * @param requestId 由宿主生成的请求标识
 * @param bindings 宿主固定的不可变业务绑定或本轮工具绑定集合
 * @param control 借用的取消、截止时间和预算控制
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@JsonIgnoreType
public record ToolInvocationContext(UUID requestId, EntitySnapshot bindings, ExecutionControl control) {
    /**
     * 校验由宿主提供的请求标识、不可变绑定和借用控制；模型参数不参与上下文构造。
     *
     * @param requestId 由宿主生成的请求标识
     * @param bindings 宿主固定的不可变业务绑定或本轮工具绑定集合
     * @param control 借用的取消、截止时间和预算控制
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
        return new ToolInvocationContext(requestId, bindings, stageControl);
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
