package org.zexnocs.teanekoagent.agent.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;

import java.util.List;
import java.util.Objects;

/**
 * 已经验证的实际交互与历史投影，只能由布局校验工厂生成，不用任意跳过条数充当证明。
 * 此值仅含不可变数据；通用序列化不重复导出整份交互和临时资料。
 * <br>2026/11/03：固定表达计划和语义投影，原模型链先完整核验。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.6.2-l
 */
@JsonIgnoreType
public final class AgentPromptTranscript {
    private final AgentPromptInput input;
    private final LLMInteractionResult interaction;
    private final List<ILLMMessage> messages;
    private final org.zexnocs.teanekoagent.expression.ReplyPlans.@org.jspecify.annotations.Nullable Approved replyPlan;

    /**
     * 2026/11/03：保留旧无表达构造，保存已验证布局，不持有转换器、控制或服务。
     * @param input 原输入布局
     * @param interaction 被绑定的不可变交互
     * @param messages 已验证的历史投影
     */
    private AgentPromptTranscript(AgentPromptInput input, LLMInteractionResult interaction, List<ILLMMessage> messages) {
        this(input, interaction, messages, null);
    }
    /**
     * 2026/11/03：只保存已核验的语义投影和不可变计划。
     * @param input 原布局
     * @param interaction 原交互
     * @param messages 已验证消息
     * @param replyPlan 可选表达计划
     */
    private AgentPromptTranscript(AgentPromptInput input, LLMInteractionResult interaction, List<ILLMMessage> messages,
            org.zexnocs.teanekoagent.expression.ReplyPlans.@org.jspecify.annotations.Nullable Approved replyPlan) {
        this.input = input; this.interaction = interaction; this.messages = messages; this.replyPlan = replyPlan;
    }

    /**
     * 在真实交互完成后验证投影，失败不生成可提交历史。
     * @param input 本轮实际布局
     * @param interaction 原始成功交互
     * @param codec 本轮协议度量器
     * @return 与该交互绑定的只读投影
     * @throws NullPointerException 当输入、交互或度量器为空时
     * @throws IllegalArgumentException 当实际输入或响应合同不成立时
     */
    public static AgentPromptTranscript create(AgentPromptInput input, LLMInteractionResult interaction, LLMMessageCodec codec) {
        Objects.requireNonNull(input, "Prompt 布局不能为空。"); Objects.requireNonNull(interaction, "Prompt 交互不能为空。");
        return new AgentPromptTranscript(input, interaction, input.project(interaction, Objects.requireNonNull(codec, "Prompt 度量器不能为空。")));
    }

    /**
     * 提供原始消息与临时片段布局，供会话独立复核，不公开可执行对象。
     * @return 固定输入布局
     */
    public AgentPromptInput input() { return input; }
    /** @return 2026/11/03：已绑定原答案的计划，旧交互为空 */
    public org.zexnocs.teanekoagent.expression.ReplyPlans.@org.jspecify.annotations.Nullable Approved replyPlan() { return replyPlan; }
    /** @return 2026/11/03：原合同核验后的最终语义答案 */
    public org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage projectedAnswer() {
        return (org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage) messages.getLast();
    }
    /**
     * 2026/11/03：先验证真实模型链，再仅替换最后正式答案，不沉积控制信封。
     * @param input 原布局
     * @param interaction 原交互
     * @param codec 原协议度量器
     * @param plan 原答案对应的已验证计划
     * @return 同时绑定原交互和发送计划的投影
     * @throws IllegalArgumentException 当原答案摘要不一致时
     */
    public static AgentPromptTranscript createExpression(AgentPromptInput input, LLMInteractionResult interaction, LLMMessageCodec codec,
            org.zexnocs.teanekoagent.expression.ReplyPlans.Approved plan) {
        var base = create(input, interaction, codec);
        if (!plan.sourceDigest().equals(org.zexnocs.teanekocore.utils.StableDigest.sha256(codec.signature(List.of(interaction.finalCandidate().orElseThrow())).toString()))) {
            throw new IllegalArgumentException("表达计划与原模型答案不一致。");
        }
        var projected = new java.util.ArrayList<>(base.messages); projected.removeLast(); projected.add(plan.answer());
        return new AgentPromptTranscript(input, interaction, org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots.messages(projected), plan);
    }


    /**
     * 取得本投影唯一绑定的原交互，结果容器不得改配另一份响应。
     * @return 原始不可变交互
     */
    public LLMInteractionResult interaction() { return interaction; }

    /**
     * 返回已验证且不含本轮临时资料的历史，提交仍需当前引用与会话代次仲裁。
     * @return 深层冻结的完整原消息及新响应
     */
    public List<ILLMMessage> messages() { return messages; }
    /**
     * 2026/11/03：邮箱重新核验原交互与编译结果，仅允许替换最后一条正式消息。
     * @param codec 原协议度量器
     * @return 重新核验的语义历史
     * @throws IllegalArgumentException 当原文或投影在交接中变化时
     */
    public List<ILLMMessage> revalidate(LLMMessageCodec codec) {
        var verified = new java.util.ArrayList<>(input.project(interaction, codec));
        if (replyPlan != null) {
            if (!replyPlan.sourceDigest().equals(org.zexnocs.teanekocore.utils.StableDigest.sha256(codec.signature(List.of(interaction.finalCandidate().orElseThrow())).toString()))) {
                throw new IllegalArgumentException("表达计划与原模型答案不一致。");
            }
            verified.removeLast(); verified.add(replyPlan.answer());
        }
        if (!codec.signature(verified).equals(codec.signature(messages))) { throw new IllegalArgumentException("已验证历史投影在交接中变化。"); }
        return List.copyOf(verified);
    }

}
