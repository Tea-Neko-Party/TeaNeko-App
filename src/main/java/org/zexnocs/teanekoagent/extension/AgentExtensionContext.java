package org.zexnocs.teanekoagent.extension;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.Objects;
import java.util.UUID;

/**
 * 宿主固定的扩展调用上下文，不进入模型参数、组件或数据库，后端不得关闭借用控制。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param requestId 原轮次标识，与 callId 共同用于后端防重
 * @param callId 原工具调用标识，资料复核使用固定观察标识
 * @param actor 真实身份、作用域及来源
 * @param phase 宿主固定的执行或资料复核阶段，不能由调用 ID 推断授权阶段
 * @param memoryReference 可选固定空间，后端不能由模型另选空间
 * @param control 借用预算、取消及截止时间
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@JsonIgnoreType
public record AgentExtensionContext(UUID requestId, String callId, AgentActorContext actor, Phase phase,
        @Nullable MemoryNamespaceReference memoryReference, ExecutionControl control) {
    /**
     * 验证真实边界，避免将其他 scope 的引用混入后端调用。
     * @param requestId 原请求
     * @param callId 原调用
     * @param actor 真实身份
     * @param phase 宿主固定阶段
     * @param memoryReference 固定引用
     * @param control 借用控制
     * @throws IllegalArgumentException 当调用或引用边界非法时
     */
    public AgentExtensionContext {
        Objects.requireNonNull(requestId); Objects.requireNonNull(actor); Objects.requireNonNull(phase); Objects.requireNonNull(control);
        if (callId == null || callId.isBlank() || callId.length() > 256 || memoryReference != null && !memoryReference.scope().equals(actor.scope())) {
            throw new IllegalArgumentException("扩展调用标识或空间边界无效。");
        }
    }
    /**
     * 真实执行与只读资料检查分开，模型不能用特殊调用 ID 伪装复核阶段。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    public enum Phase { EXECUTION, REFERENCE_CHECK }
}
