package org.zexnocs.teanekoagent.runtime;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptTranscript;
import org.zexnocs.teanekocore.actuator.execution.BudgetKey;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 一轮已结束的只读结果。被拒绝或终止时仍保留已观察交互，不自动提交历史或发送平台消息。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param agentId 实际执行的实例标识
 * @param requestId 宿主请求标识
 * @param status 最终业务状态
 * @param access 最近一次允许检查或首次拒绝的非敏感原因
 * @param interaction 已观察完成的完整交互诊断；尚未进入或端口未响应时为空
 * @param budgetUsage 交付时宿主共享总预算读数，派生轮次不把它冒充自身增量
 * @param conversation 会话拥有者固定的代次；独立执行或未准入时可为空，出站前仍需复核
 * @param memoryReference 实际固定的持久化空间引用；未启用此能力或解析失败时为空
 * @param failure 可选的安全失败诊断，不含供应商原始正文
 * @param promptTranscript 可选已验证的本轮临时输入/历史投影，原交互诊断始终保留实际模型输入
 * <br>2026/11/03：公开已验证语义答案及固定表达计划，不改变原构造入口。
 * <br>2026/11/05：轮次结果传递安全失败诊断与中文原因。
 *
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record AgentRunResult(EntityId agentId, UUID requestId, Status status,
                             Optional<AgentAccessDecision> access, Optional<LLMInteractionResult> interaction,
                             Map<BudgetKey, ResourceBudget.Usage> budgetUsage, @Nullable AgentConversationStamp conversation,
                             @Nullable IAgentContextReference memoryReference, @Nullable AgentPromptTranscript promptTranscript,
                             @Nullable FailureDiagnostic failure) {
    /**
     * 2026/11/05：保持原结果构造签名，自动继承 LLM 已知的安全失败原因。
     * @param agentId 实例
     * @param requestId 请求
     * @param status 状态
     * @param access 门控结果
     * @param interaction 交互结果
     * @param budgetUsage 预算
     * @param conversation 会话代次
     * @param memoryReference 记忆引用
     * @param promptTranscript 已验证投影
     */
    public AgentRunResult(EntityId agentId, UUID requestId, Status status,
            Optional<AgentAccessDecision> access, Optional<LLMInteractionResult> interaction,
            Map<BudgetKey, ResourceBudget.Usage> budgetUsage, @Nullable AgentConversationStamp conversation,
            @Nullable IAgentContextReference memoryReference, @Nullable AgentPromptTranscript promptTranscript) {
        this(agentId, requestId, status, access, interaction, budgetUsage, conversation, memoryReference,
                promptTranscript, interaction.map(LLMInteractionResult::failure).orElse(null));
    }

    /**
     * 2026/11/05：保留流水线失败诊断，不保存 Throwable 或不可信错误文本。
     * @param diagnostic 可选安全诊断
     * @return 相同业务结果及诊断
     */
    public AgentRunResult withFailure(@Nullable FailureDiagnostic diagnostic) {
        return new AgentRunResult(agentId, requestId, status, access, interaction, budgetUsage, conversation,
                memoryReference, promptTranscript, diagnostic);
    }

    /** @return 2026/11/05：用户可读的失败原因，缺少异常时使用明确状态解释 */
    public String failureDescription() {
        if (failure != null) {
            return failure.description();
        }
        return switch (status) {
            case CONFIGURATION_ERROR -> "模型或 Agent 配置不可用，请检查路由、参数和组件装配。";
            case INVALID_INPUT -> "模型输入不符合当前协议或能力限制。";
            case INVALID_RESPONSE -> "模型返回了无法解析的响应。";
            case EMPTY_OUTPUT -> "模型没有返回可发送的内容。";
            case TRUNCATED -> "模型输出被截断，请检查输出额度和上下文容量。";
            case REFUSED -> "模型拒绝了本次请求。";
            case BUDGET_EXCEEDED -> "本轮调用次数或预算不足。";
            case TIMED_OUT, QUEUE_TIMED_OUT -> "请求超过允许的等待或执行时限。";
            case CANCELLED -> "请求已取消。";
            case SKIPPED -> "当前聊天或请求未通过执行条件。";
            default -> "请求未完成，状态：" + status;
        };
    }
    /**
     * 保留没有临时 Prompt 投影的既有结果构造。
     * @param agentId 实际实例
     * @param requestId 原请求标识
     * @param status 最终状态
     * @param access 可选门控诊断，null 表示尚未取得
     * @param interaction 可选原始交互，null 表示尚未观察
     * @param budgetUsage 预算读数
     * @param conversation 可选会话代次
     * @param memoryReference 可选空间引用
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当结果合同不一致时
     */
    public AgentRunResult(EntityId agentId, UUID requestId, Status status, @Nullable AgentAccessDecision access,
            @Nullable LLMInteractionResult interaction, Map<BudgetKey, ResourceBudget.Usage> budgetUsage,
            @Nullable AgentConversationStamp conversation, @Nullable IAgentContextReference memoryReference) {
        this(agentId, requestId, status, Optional.ofNullable(access), Optional.ofNullable(interaction), budgetUsage, conversation, memoryReference, null);
    }
    /**
     * 构造尚未关联持久化空间的会话结果，保留既有调用方式。
     * @param agentId 实际实例
     * @param requestId 原始请求 ID
     * @param status 最终状态
     * @param access 可选门控诊断，null 表示尚未取得
     * @param interaction 可选已观察交互，null 表示尚未观察
     * @param budgetUsage 预算读数
     * @param conversation 可选会话代次
     * @throws NullPointerException 当必需结果字段为空时
     * @throws IllegalArgumentException 当成功数据或会话实例不一致时
     */
    public AgentRunResult(EntityId agentId, UUID requestId, Status status, @Nullable AgentAccessDecision access,
            @Nullable LLMInteractionResult interaction, Map<BudgetKey, ResourceBudget.Usage> budgetUsage,
            @Nullable AgentConversationStamp conversation) {
        this(agentId, requestId, status, access, interaction, budgetUsage, conversation, null);
    }

    /**
     * 构造没有会话绑定的独立执行结果，保留原有端口的构造方式。
     * @param agentId 实际实例
     * @param requestId 请求标识
     * @param status 最终状态
     * @param access 可选门控结果，null 表示尚未取得
     * @param interaction 可选已观察交互，null 表示尚未观察
     * @param budgetUsage 预算读数
     * @throws NullPointerException 当必需结果字段为空时
     * @throws IllegalArgumentException 当成功结果缺少成功交互或准入时
     */
    public AgentRunResult(EntityId agentId, UUID requestId, Status status, @Nullable AgentAccessDecision access,
            @Nullable LLMInteractionResult interaction, Map<BudgetKey, ResourceBudget.Usage> budgetUsage) {
        this(agentId, requestId, status, access, interaction, budgetUsage, null);
    }

    /**
     * 固定结果容器，成功必须同时具有完整交互及允许的门控结果。
     * @param agentId Agent 实例
     * @param requestId 宿主请求标识
     * @param status 最终状态
     * @param access 门控诊断
     * @param interaction 可选交互快照
     * @param budgetUsage 宿主预算观察值
     * @param conversation 可选可信会话代次
     * @param memoryReference 可选实际空间引用
     * @param promptTranscript 可选已验证历史投影，必须绑定同一份原始交互
     * @param failure 可选安全失败诊断
     * @throws NullPointerException 当必需结果字段为空时
     * @throws IllegalArgumentException 当成功结果缺少成功交互或准入、代次属于其他实例，或会话与空间作用域不一致时
     */
    public AgentRunResult {
        Objects.requireNonNull(agentId, "Agent 实例标识不能为空。");
        Objects.requireNonNull(requestId, "Agent 请求标识不能为空。");
        Objects.requireNonNull(status, "Agent 结果状态不能为空。");
        Objects.requireNonNull(access, "门控结果容器不能为空。");
        Objects.requireNonNull(interaction, "交互结果容器不能为空。");
        budgetUsage = Map.copyOf(budgetUsage);
        if (promptTranscript != null && interaction.orElse(null) != promptTranscript.interaction()) {
            throw new IllegalArgumentException("Prompt 历史投影与结果的原交互不一致。");
        }
        if (conversation != null && !conversation.key().agentId().equals(agentId)) {
            throw new IllegalArgumentException("结果会话代次不属于当前 Agent 实例。");
        }
        if (conversation != null && memoryReference != null && !conversation.key().scope().equals(memoryReference.scope())) {
            throw new IllegalArgumentException("结果的会话与空间引用作用域不一致。");
        }
        if (status == Status.SUCCESS && (access.filter(AgentAccessDecision::allowed).isEmpty()
                || interaction.filter(value -> value.status() == LLMInteractionResult.Status.SUCCESS).isEmpty())) {
            throw new IllegalArgumentException("Agent 成功结果必须包含有效准入和完整成功交互。");
        }
    }

    /**
     * 只对最终成功公开回答候选，门控已失效的模型正文只保留在诊断交互中。
     * @return 当前允许交付的最终消息
     */
    public Optional<ILLMAssistantMessage> answer() {
        // 2026/11/03：平台协议控制符只保留在诊断中，公开答案使用已核验语义投影。
        if (status == Status.SUCCESS && promptTranscript != null && promptTranscript.replyPlan() != null) { return Optional.of(promptTranscript.projectedAnswer()); }
        return status == Status.SUCCESS ? interaction.flatMap(LLMInteractionResult::finalCandidate) : Optional.empty();
    }

    /**
     * 提供已验证的完整历史投影；本轮临时资料不重复沉积，提交仍须校验原输入、代次和当前门控。
     * @return 只有本轮成功时存在的完整消息链
     */
    public Optional<List<ILLMMessage>> committableTranscript() {
        return status != Status.SUCCESS ? Optional.empty() : promptTranscript != null ? Optional.of(promptTranscript.messages())
                : interaction.flatMap(LLMInteractionResult::committableTranscript);
    }

    /** @return 2026/11/03：成功轮次冻结的表达计划；旧宿主为空 */
    public org.zexnocs.teanekoagent.runtime.delivery.@Nullable IAgentReplyPlan replyPlan() {
        return status == Status.SUCCESS && promptTranscript != null ? promptTranscript.replyPlan() : null;
    }
    /**
     * 稳定业务状态，配置拒绝和执行取消与供应商终止分开表达。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    public enum Status { SUCCESS, SKIPPED, CONFIGURATION_ERROR, CANCELLED, TIMED_OUT, BUDGET_EXCEEDED,
        EMPTY_OUTPUT, TRUNCATED, REFUSED, INVALID_INPUT, INVALID_RESPONSE, FAILED,
        BUSY, QUEUE_TIMED_OUT, REENTRANT, DUPLICATE_REQUEST, STALE_CONVERSATION,
        INPUT_TOO_LARGE, REQUEST_CONFLICT, REPLAY_UNAVAILABLE }
}
