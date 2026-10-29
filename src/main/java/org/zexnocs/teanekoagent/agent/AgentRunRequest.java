package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 宿主规范化的一次请求，不接受模型提供的身份、执行器或配置绑定。
 * @param requestId 宿主生成的请求标识，不自动提供跨请求幂等
 * @param actor 可信发起者、来源和目标
 * @param activity 本次业务活动类别
 * @param messages 默认会话仅传本次新增输入，历史由邮箱装配；独立端口可显式传完整历史
 * @param conversation 宿主会话代次；显式独立轮次可为空，默认邮箱始终重新绑定
 * @param memoryReference 邮箱固定的空间引用；默认入口忽略调用者提示，独立入口重新复核
 * @param userText 当前原始用户文本凭据，不从 messages 历史推导；缺失时不授权明确新增
 * @param inputMaxBytes 宿主完整模型输入协议字节上限；默认邮箱覆盖为本轮窗口策略，旧独立入口保持不额外限额
 * @param personalityIdentity 邮箱固定的人格身份提示，不携带人格正文，实际源仍须重新解析
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity,
                              List<ILLMMessage> messages, @Nullable AgentConversationStamp conversation,
                              @Nullable MemoryNamespaceReference memoryReference, @Nullable AgentUserText userText, long inputMaxBytes,
                              @Nullable String personalityIdentity) {
    /**
     * 保留没有人格身份交接的完整请求构造。
     * @param requestId 原请求
     * @param actor 可信来源
     * @param activity 活动
     * @param messages 原消息
     * @param conversation 可选会话代次
     * @param memoryReference 可选固定记忆引用
     * @param userText 可选当前原文
     * @param inputMaxBytes 完整输入预算
     */
    public AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity, List<ILLMMessage> messages,
            @Nullable AgentConversationStamp conversation, @Nullable MemoryNamespaceReference memoryReference, @Nullable AgentUserText userText, long inputMaxBytes) {
        this(requestId, actor, activity, messages, conversation, memoryReference, userText, inputMaxBytes, null);
    }
    /**
     * 保留已有原文凭据调用，显式独立入口没有额外协议字节限额，仍受原模型/token 预算约束。
     * @param requestId 原请求标识
     * @param actor 可信身份
     * @param activity 宿主活动
     * @param messages 原消息
     * @param conversation 可选会话代次
     * @param memoryReference 可选空间引用
     * @param userText 可选原文凭据
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当身份或消息合同非法时
     */
    public AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity, List<ILLMMessage> messages,
            @Nullable AgentConversationStamp conversation, @Nullable MemoryNamespaceReference memoryReference, @Nullable AgentUserText userText) {
        this(requestId, actor, activity, messages, conversation, memoryReference, userText, Long.MAX_VALUE);
    }
    /**
     * 保留没有原文凭据的调用方式，不因消息中包含明确前缀而自动授权新增。
     * @param requestId 原请求标识
     * @param actor 可信身份
     * @param activity 宿主活动
     * @param messages 统一消息
     * @param conversation 可选会话代次
     * @param memoryReference 可选固定空间引用
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当身份或消息协议非法时
     */
    public AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity, List<ILLMMessage> messages,
            @Nullable AgentConversationStamp conversation, @Nullable MemoryNamespaceReference memoryReference) {
        this(requestId, actor, activity, messages, conversation, memoryReference, null);
    }
    /**
     * 保留无持久化空间的会话输入构造方式。
     * @param requestId 请求标识
     * @param actor 可信身份
     * @param activity 执行活动
     * @param messages 统一消息
     * @param conversation 可选会话代次
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当身份边界或消息协议非法时
     */
    public AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity,
            List<ILLMMessage> messages, @Nullable AgentConversationStamp conversation) {
        this(requestId, actor, activity, messages, conversation, null);
    }
    /**
     * 创建尚未绑定会话的宿主输入，交给默认邮箱时由邮箱决定真实代次。
     * @param requestId 宿主请求标识
     * @param actor 可信身份与固定目标
     * @param activity 宿主选择的活动
     * @param messages 初始统一消息
     * @throws IllegalArgumentException 当消息协议或透明状态无法安全冻结时
     * @throws NullPointerException 当必需字段为空时
     */
    public AgentRunRequest(UUID requestId, AgentActorContext actor, AgentActivity activity, List<ILLMMessage> messages) {
        this(requestId, actor, activity, messages, null);
    }

    /**
     * 深层冻结输入，交付后修改原消息不会影响本轮。
     * @param requestId 宿主请求标识
     * @param actor 可信身份与固定目标
     * @param activity 宿主选择的活动
     * @param messages 初始统一消息
     * @param conversation 可选可信会话代次，不进入 Prompt metadata
     * @param memoryReference 可选可信空间引用，不进入模型参数
     * @param userText 可选当前原始用户文本，不允许系统任务或其他来源消息携带
     * @param inputMaxBytes 宿主固定的完整协议字节预算，必须为正
     * @param personalityIdentity 可选人格身份提示，不能作为授权或人格正文
     * @throws IllegalArgumentException 当消息协议或透明状态无法安全冻结时
     * @throws NullPointerException 当必需字段为空时
     */
    public AgentRunRequest {
        Objects.requireNonNull(requestId, "Agent 请求标识不能为空。");
        Objects.requireNonNull(actor, "Agent 请求身份不能为空。");
        Objects.requireNonNull(activity, "Agent 活动不能为空。");
        if (inputMaxBytes <= 0) { throw new IllegalArgumentException("Agent 完整输入字节预算必须为正。"); }
        if (conversation != null && !conversation.key().scope().equals(actor.scope())) {
            throw new IllegalArgumentException("会话代次与请求作用域不一致。");
        }
        if (memoryReference != null && !memoryReference.scope().equals(actor.scope())) {
            throw new IllegalArgumentException("记忆引用与请求作用域不一致。");
        }
        if (userText != null && (actor.origin() != AgentActorContext.Origin.USER || !userText.sourceId().equals(actor.sourceId()))) {
            throw new IllegalArgumentException("原始用户文本与请求来源不一致。");
        }
        messages = LLMProtocolSnapshots.messages(messages);
    }
}
