package org.zexnocs.teanekoagent.component;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

// 2026/11/04：组件数据与系统按职责统一归类，持久标识保持不变。

import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptTranscript;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.EntityId;

/**
 * Agent 底座的类型化数据键；组件仅保存不可变定义、状态和可信身份，不保存服务。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * 修改记录：<br>
 * 2026/11/04：组件或系统迁入统一的 component 分类，保持原执行语义。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public final class AgentComponents {
    // 2026/11/04：插件选择也是轮次组件；键归入统一目录，原标识保持不变。
    public static final ComponentKey<AgentPluginSelection> PLUGIN_SELECTION =
            ComponentKey.of("agent.plugin-selection", AgentPluginSelection.class);
    /** 当前会话已固定的人格身份，不承载人格正文或授权。 */
    public static final ComponentKey<String> PERSONALITY_IDENTITY = ComponentKey.of("agent.personality-identity", String.class);
    /** 当前补正引用，仅用于共享 key 管理后的会话协调，不承载数据库对象或正文。 */
    public static final ComponentKey<String> CORRECTION_KEY = ComponentKey.of("agent.correction-key", String.class);
    public static final ComponentKey<AgentDefinition> DEFINITION = ComponentKey.of("agent.definition", AgentDefinition.class);
    public static final ComponentKey<Boolean> CLOSED = ComponentKey.of("agent.closed", Boolean.class);
    public static final ComponentKey<AgentActorContext> ACTOR = ComponentKey.of("agent.actor", AgentActorContext.class);
    public static final ComponentKey<AgentActivity> ACTIVITY = ComponentKey.of("agent.activity", AgentActivity.class);
    public static final ComponentKey<AgentScopePolicy> SCOPE_POLICY = ComponentKey.of("agent.scope-policy", AgentScopePolicy.class);
    public static final ComponentKey<AgentRuntimeSnapshot> RUNTIME = ComponentKey.of("agent.runtime", AgentRuntimeSnapshot.class);
    public static final ComponentKey<EntityId> INSTANCE = ComponentKey.of("agent.instance", EntityId.class);
    public static final ComponentKey<AgentRunRequest> REQUEST = ComponentKey.of("agent.request", AgentRunRequest.class);
    public static final ComponentKey<AgentConversationStamp> CONVERSATION = ComponentKey.of("agent.conversation", AgentConversationStamp.class);
    public static final ComponentKey<MemoryNamespaceReference> MEMORY_REFERENCE = ComponentKey.of("agent.memory-reference", MemoryNamespaceReference.class);
    public static final ComponentKey<LLMInteractionResult> INTERACTION = ComponentKey.of("agent.interaction", LLMInteractionResult.class);
    public static final ComponentKey<AgentPromptInput> PROMPT_INPUT = ComponentKey.of("agent.prompt-input", AgentPromptInput.class);
    public static final ComponentKey<AgentPromptTranscript> PROMPT_TRANSCRIPT = ComponentKey.of("agent.prompt-transcript", AgentPromptTranscript.class);
    public static final ComponentKey<AgentRunResult> OUTCOME = ComponentKey.of("agent.outcome", AgentRunResult.class);

    /** 禁止实例化组件键集合。 */
    private AgentComponents() {}
}
