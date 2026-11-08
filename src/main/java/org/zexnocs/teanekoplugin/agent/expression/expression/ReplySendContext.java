package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：表达业务归入插件，通用交付能力独立于业务组件。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
/**
 * 资源发送借用的原请求、目标与守卫，不进入冻结计划或持久化。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：提供明确的借用资源 getter，保留旧访问器和原拥有者的关闭责任。
 * @param data 原消息目标
 * @param request 原请求
 * @param index 原步骤号
 * @param control 协调控制
 * @param guard 每次不可逆动作前须等待的原守卫
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@JsonIgnoreType
public record ReplySendContext(ITeaNekoMessageData data, AgentRunRequest request, int index,
        ExecutionControl control, Supplier<CompletionStage<Void>> guard) {
    /**
     * 2026/11/05：发送步骤借用协调控制，直到真实回执完成仍由外层拥有者负责释放。
     * @return 原发送协调控制，不转移关闭责任
     */
    public ExecutionControl getControl() {
        return control;
    }
}
