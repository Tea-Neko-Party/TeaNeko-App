package org.zexnocs.teanekoagent.agent.conversation;

import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 仅含完整成功轮次的不可变窗口；丢弃按整轮进行，不能把截断伪装成摘要。
 * @param turns 从旧到新的完整轮次
 * @param discardedTurns 因窗口或输入限制丢弃的轮次数
 * @param lastTurnDiscarded 最近完成轮次是否未能保留
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentConversationWindow(List<Turn> turns, long discardedTurns, boolean lastTurnDiscarded) {
    public static final ComponentKey<AgentConversationWindow> COMPONENT = ComponentKey.of("agent.conversation-window", AgentConversationWindow.class);

    /**
     * 固定轮次容器，元素在各自构造边界深层冻结。
     * @param turns 完整轮次
     * @param discardedTurns 丢弃计数
     * @param lastTurnDiscarded 最近轮次未保留标记
     * @throws NullPointerException 当轮次列表或其元素为空时
     */
    public AgentConversationWindow { turns = List.copyOf(turns); }

    /**
     * 创建没有历史或丢弃记录的新代次窗口。
     * @return 空窗口
     */
    public static AgentConversationWindow empty() { return new AgentConversationWindow(List.of(), 0, false); }

    /**
     * 扁平读取完整轮次，不复制或拆开消息内容。
     * @return 保持轮次顺序的不可变消息列表
     */
    public List<ILLMMessage> messages() { return turns.stream().flatMap(turn -> turn.messages().stream()).toList(); }

    /**
     * 保守累加各轮协议 JSON 字节，不通过整数溢出扩大输入额度。
     * @return 饱和累计字节数
     */
    public long bytes() {
        long total = 0;
        for (var turn : turns) { total = add(total, turn.signature().bytes()); }
        return total;
    }

    /**
     * 移除最旧完整轮次，供消息与字节限制共同使用。
     * @return 新窗口；已经为空时返回自身
     */
    public AgentConversationWindow dropOldest() {
        return turns.isEmpty() ? this : new AgentConversationWindow(turns.subList(1, turns.size()), add(discardedTurns, 1), lastTurnDiscarded);
    }

    /**
     * 根据本轮策略保留最近完整轮次，不进行模型摘要。
     * @param options 本轮固定策略
     * @return 满足窗口消息与字节限制的窗口
     */
    public AgentConversationWindow trim(AgentConversationOptions options) {
        var result = this;
        while (!result.turns().isEmpty() && (result.messages().size() > options.maxHistoryMessages() || result.bytes() > options.maxInputBytes())) {
            result = result.dropOldest();
        }
        return result;
    }

    /**
     * 追加成功轮次；单轮过大时丢弃整个窗口，避免跳过中间回答后继续使用旧上下文。
     * @param turn 完整成功轮次
     * @param options 本轮策略
     * @return 新窗口和明确丢弃标记
     */
    public AgentConversationWindow append(Turn turn, AgentConversationOptions options) {
        if (turn.messages().size() > options.maxHistoryMessages() || turn.signature().bytes() > options.maxInputBytes()) {
            return new AgentConversationWindow(List.of(), add(discardedTurns, turns.size() + 1L), true);
        }
        var next = new ArrayList<>(turns); next.add(turn);
        return new AgentConversationWindow(next, discardedTurns, false).trim(options);
    }

    /**
     * 饱和累加非负计量，溢出后仍保持超限。
     * @param first 已记录值
     * @param second 本次增量
     * @return 饱和累加值
     */
    private static long add(long first, long second) { return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second; }

    /**
     * 一个成功请求新增的完整消息链，包含真实发起者，消息使用统一协议。
     * @param requestId 宿主请求 ID
     * @param actor 该轮真实发起者和来源
     * @param messages 本轮新增输入及完整响应，不包含已有历史前缀
     * @param signature 消息的规范化度量
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Turn(UUID requestId, AgentActorContext actor, List<ILLMMessage> messages, LLMMessageCodec.Signature signature) {
        /**
         * 深层冻结本轮消息，组件不能保留调用方可修改的 DTO。
         * @param requestId 请求 ID
         * @param actor 可信身份
         * @param messages 完整新增消息
         * @param signature 协议指纹及字节数
         * @throws NullPointerException 当请求、身份或度量为空时
         * @throws IllegalArgumentException 当消息不能冻结为有效协议数据时
         */
        public Turn {
            Objects.requireNonNull(requestId, "窗口请求标识不能为空。");
            Objects.requireNonNull(actor, "窗口发起者不能为空。");
            Objects.requireNonNull(signature, "窗口消息度量不能为空。");
            messages = LLMProtocolSnapshots.messages(messages);
        }
    }
}
