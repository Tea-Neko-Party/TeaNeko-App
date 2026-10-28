package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;

import java.util.Objects;
import java.util.Set;

/**
 * 可共享的只读 Agent 定义，不包含实例会话、工具执行器或作用域授权。
 * @param id 稳定定义标识
 * @param modelOptions 宿主选择的模型覆盖快照，默认路由仍由本地配置解析
 * @param activities 本定义允许参与的活动
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentDefinition(String id, LLMModelOptionsSnapshot modelOptions, Set<AgentActivity> activities) {
    /**
     * 固定定义和能力集合；创建定义不会加载配置、启动模型或授予 scope 权限。
     * @param id 定义标识
     * @param modelOptions 模型参数快照
     * @param activities 允许的活动集合
     */
    public AgentDefinition {
        AgentScope.requireIdentity(id);
        Objects.requireNonNull(modelOptions, "模型参数快照不能为空。");
        activities = Set.copyOf(activities);
    }
}
