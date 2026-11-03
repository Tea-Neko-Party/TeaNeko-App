package org.zexnocs.teanekoagent.plugin;

import lombok.NonNull;
import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;

/**
 * 本轮插件操作的显式可信上下文；控制仅借用，组件值不得保存本对象。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param definition 实例定义
 * @param request 原可信请求
 * @param runtime 固定预算及模型设置
 * @param snapshot 当前只读组件
 * @param control 借用轮次控制
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
@com.fasterxml.jackson.annotation.JsonIgnoreType
public record AgentPluginContext(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime,
                                 EntitySnapshot snapshot, ExecutionControl control) {
    /** @return 只含关联 ID 的诊断，不记录原文、模型参数或执行资源 */
    @Override @NonNull
    public String toString() { return "AgentPluginContext[请求=" + request.requestId() + "，上下文已隐藏]"; }
}
