package org.zexnocs.teanekoagent.component.proactive;

import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.proactive.adapter.ProactiveTargets;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor.Delivery;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 可选组件向已有主动候选贡献资格和上下文，不另起调度器或绕过 ACTIVE 门控。
 * <br>2026/11/06：候选使用前复核，真实结果后才提交关怀计数。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
public interface IProactiveContextSource {
    /**
     * @param target 原可信目标
     * @param kind 发生类型
     * @return 当前已装配且需要参与本次候选
     */
    boolean applies(ProactiveTargets.Target target, AgentTriggerGate.Kind kind);
    /**
     * @param target 原目标
     * @param reference 固定空间
     * @param request 原发生身份
     * @param control 借用控制
     * @return 空表示条件未满足，应当跳过模型；存在表示贡献可复核候选
     */
    CompletionStage<Optional<Session>> prepare(ProactiveTargets.Target target, MemoryNamespaceReference reference,
                                                UUID request, ExecutionControl control);
    /**
     * 当前候选的短生命周期观察，不进入 ECS 持久序列化。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.8.6-l
     */
    interface Session {
        /** @return 有界的候选缘由，不授予工具或发送权限 */
        String context();
        /**
         * @param control 当前控制
         * @return 原候选仍有效时完成
         */
        CompletionStage<Void> verify(ExecutionControl control);
        /**
         * @param result 已观察真实结果
         * @return 状态实际提交；未知发送不得当成未发而自动重试
         */
        CompletionStage<Void> finish(Delivery result);
    }
}
