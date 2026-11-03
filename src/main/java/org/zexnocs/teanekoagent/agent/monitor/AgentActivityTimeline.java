package org.zexnocs.teanekoagent.agent.monitor;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentScope;

import java.time.Instant;
import java.util.*;

/**
 * 只记录真实生命周期节点的有界进程时间线，不保存 Prompt、消息、工具参数或异常正文。
 * 观测不参与业务成功判定，进程重启和窗口截断通过明确字段报告。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class AgentActivityTimeline {
    private final UUID instance = UUID.randomUUID();
    private final Instant startedAt = Instant.now();
    private final Deque<Entry> entries = new ArrayDeque<>();
    private long sequence;

    /**
     * @param request 原请求，只提取可信定位
     * @param stage 实际宿主阶段 ID
     * @param state 实际观察状态
     */
    public void mark(AgentRunRequest request, String stage, String state) {
        append(request.requestId(), request.actor().scope(), request.activity().name(), stage, state, Map.of());
    }

    /**
     * @param report 已完成的脱敏模型报告
     */
    public void finish(AgentTurnReport report) {
        append(report.requestId(), report.scope(), report.activity().name(), "agent.turn", report.status().name(),
                Map.of("elapsedMillis", report.elapsedMillis(), "modelCalls", (long) report.modelCalls(),
                        "toolCalls", (long) report.toolCalls(), "reportedTokens", report.reportedTokens(),
                        "estimatedTokens", report.estimatedTokens(), "unknownUsageAttempts", (long) report.unknownUsageAttempts()));
    }

    /**
     * @param request 原请求
     * @param scope 原发送目标
     * @param index 实际发送段号
     * @param state 平台实际调用或完成状态
     */
    public void delivery(UUID request, AgentScope scope, int index, String state) {
        append(request, scope, "DELIVERY", "delivery.part." + index, state, Map.of());
    }

    /**
     * @param request 原请求
     * @param scope 原范围
     * @param activity 原活动
     * @param stage 实际阶段
     * @param state 实际状态
     * @param metrics 已脱敏计数
     */
    private synchronized void append(UUID request, AgentScope scope, String activity, String stage,
                                      String state, Map<String, Long> metrics) {
        if (entries.size() >= 4096) {
            entries.removeFirst();
        }
        entries.addLast(new Entry(++sequence, request, scope, activity, stage, state, Instant.now(), metrics));
    }

    /**
     * @param scope 已授权范围
     * @param after 原观察序号
     * @param limit 有界页面
     * @return 最新首屏或稳定增量，窗口过期须重新读取
     */
    public synchronized Page page(AgentScope scope, long after, int limit) {
        if (after < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("活动查询参数无效。");
        }
        long oldest = entries.isEmpty() ? sequence + 1 : entries.getFirst().sequence();
        boolean reset = after > sequence || after != 0 && after < oldest - 1;
        long effective = reset ? 0 : after;
        var matched = entries.stream().filter(entry -> entry.scope().equals(scope)
                && entry.sequence() > effective).toList();
        long skip = effective == 0 ? Math.max(0, matched.size() - limit) : 0;
        var items = matched.stream().skip(skip).limit(limit).map(entry ->
                new Event(entry.sequence(), entry.requestId(), entry.activity(), entry.stage(), entry.state(),
                        entry.at(), entry.metrics())).toList();
        long next = items.isEmpty() ? sequence : items.getLast().sequence();
        return new Page(instance, startedAt, reset, oldest > 1, next, items);
    }

    /**
     * 内部范围留在窗口，不导出客户端可修改的真实 scope。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param sequence 单调观察序号
     * @param requestId 原请求
     * @param scope 原目标
     * @param activity 实际活动
     * @param stage 实际阶段
     * @param state 实际状态
     * @param at 观察时刻
     * @param metrics 脱敏计数
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Entry(long sequence, UUID requestId, AgentScope scope, String activity, String stage,
                         String state, Instant at, Map<String, Long> metrics) {}

    /**
     * 传输视图不携带业务正文和执行句柄。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param sequence 实际观察序号
     * @param requestId 原请求
     * @param activity 实际活动
     * @param stage 稳定阶段 ID
     * @param state 实际状态
     * @param at 观察时刻
     * @param metrics 脱敏计数
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Event(long sequence, UUID requestId, String activity, String stage, String state,
                        Instant at, Map<String, Long> metrics) {}

    /**
     * 固定进程的有界增量，首屏展示最近数据，重启后不能接续旧游标。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param instance 本进程窗口标识
     * @param startedAt 窗口开始时刻
     * @param reset 原游标已失效
     * @param truncated 窗口已丢弃更早节点
     * @param next 下一观察序号
     * @param events 已授权节点
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Page(UUID instance, Instant startedAt, boolean reset, boolean truncated, long next,
                       List<Event> events) {}
}
