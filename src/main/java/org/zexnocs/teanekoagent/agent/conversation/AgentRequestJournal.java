package org.zexnocs.teanekoagent.agent.conversation;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentActivity;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentRunResult;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 当前会话代次内的有界完成记录；失败诊断与正常历史分离，正文丢弃后仍保留状态。
 * @param entries 从旧到新的完成记录
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentRequestJournal(List<Completed> entries) {
    public static final ComponentKey<AgentRequestJournal> COMPONENT = ComponentKey.of("agent.request-journal", AgentRequestJournal.class);

    /**
     * 固定完成记录，记录只持有不可变数据，不持有 Future 或执行控制。
     * @param entries 已完成记录
     * @throws NullPointerException 当记录列表或其元素为空时
     */
    public AgentRequestJournal { entries = List.copyOf(entries); }

    /**
     * 创建新代次的空记录。
     * @return 空完成记录
     */
    public static AgentRequestJournal empty() { return new AgentRequestJournal(List.of()); }

    /**
     * 查找已保留记录，不存在不代表可以安全重发外部动作。
     * @param requestId 原始请求 ID
     * @return 仍保留的完成记录
     */
    public Optional<Completed> find(UUID requestId) { return entries.stream().filter(entry -> entry.requestId().equals(requestId)).findFirst(); }

    /**
     * 保存状态并优先为最近记录保留正文，预算不足时移除正文而不重新执行请求。
     * @param requestId 请求 ID
     * @param identity 原始请求身份及消息指纹
     * @param result 实际交付结果
     * @param bytes 锁外取得的结果序列化度量
     * @param completedAt 完成时刻
     * @param options 本轮缓存策略
     * @return 有界的新完成记录
     */
    public AgentRequestJournal append(UUID requestId, Identity identity, AgentRunResult result, long bytes, Instant completedAt,
            AgentConversationOptions options) {
        var next = new ArrayList<>(entries);
        next.removeIf(entry -> entry.requestId().equals(requestId));
        next.add(new Completed(requestId, identity, result.status(), completedAt, bytes == Long.MAX_VALUE ? null : result,
                bytes == Long.MAX_VALUE ? 0 : bytes));
        while (next.size() > options.maxCompletedRequests()) { next.removeFirst(); }
        long remaining = options.maxReplayBytes();
        for (int i = next.size() - 1; i >= 0; i--) {
            var entry = next.get(i);
            if (entry.result() == null) { continue; }
            if (entry.bytes() <= remaining) { remaining -= entry.bytes(); }
            else { next.set(i, new Completed(entry.requestId(), entry.identity(), entry.status(), entry.completedAt(), null, 0)); }
        }
        return new AgentRequestJournal(next);
    }

    /**
     * 不含可执行对象的请求身份，防止同 ID 不同操作者或参数取得原结果。
     * @param actor 完整可信身份与来源
     * @param activity 原始活动
     * @param messagesSha256 原消息协议及可选原始用户文本凭据的完整输入指纹
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Identity(AgentActorContext actor, AgentActivity activity, String messagesSha256) {}

    /**
     * 完成状态与可选结果；正文为空表示已知完成但不再保留可重放结果。
     * @param requestId 请求 ID
     * @param identity 可信请求身份
     * @param status 实际结束状态
     * @param completedAt 完成时刻
     * @param result 可选完整结果
     * @param bytes 结果正文的序列化度量
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Completed(UUID requestId, Identity identity, AgentRunResult.Status status, Instant completedAt,
                            @Nullable AgentRunResult result, long bytes) {}
}
