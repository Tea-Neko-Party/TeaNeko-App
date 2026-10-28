package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;

/**
 * Agent 底座的类型化数据键；组件仅保存不可变定义、状态和可信身份，不保存服务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentComponents {
    public static final ComponentKey<AgentDefinition> DEFINITION = ComponentKey.of("agent.definition", AgentDefinition.class);
    public static final ComponentKey<Boolean> CLOSED = ComponentKey.of("agent.closed", Boolean.class);
    public static final ComponentKey<AgentActorContext> ACTOR = ComponentKey.of("agent.actor", AgentActorContext.class);
    public static final ComponentKey<AgentActivity> ACTIVITY = ComponentKey.of("agent.activity", AgentActivity.class);
    public static final ComponentKey<AgentScopePolicy> SCOPE_POLICY = ComponentKey.of("agent.scope-policy", AgentScopePolicy.class);
    public static final ComponentKey<AgentRuntimeSnapshot> RUNTIME = ComponentKey.of("agent.runtime", AgentRuntimeSnapshot.class);

    /** 禁止实例化组件键集合。 */
    private AgentComponents() {}
}
