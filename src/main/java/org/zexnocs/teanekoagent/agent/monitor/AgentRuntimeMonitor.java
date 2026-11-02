package org.zexnocs.teanekoagent.agent.monitor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentRunResult;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekocore.event.interfaces.IEventService;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;

/**
 * 有界进程统计与异步监控发布，监听失败、拥塞及执行器关闭均与主链隔离。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class AgentRuntimeMonitor {
    private final Map<AgentScope, List<AgentTurnReport>> reports = new LinkedHashMap<>();
    private final Map<AgentScope, EnumMap<Delivery, Long>> deliveries = new LinkedHashMap<>();
    private final Semaphore publishing = new Semaphore(64);
    private final LongAdder dropped = new LongAdder();
    private final Executor executor;
    private final ObjectProvider<IEventService> events;

    /**
     * 事件总线延迟解析，构造不触发扫描或创建后台线程。
     * @param executor 借用业务域
     * @param events 原事件服务的延迟入口
     */
    public AgentRuntimeMonitor(@Qualifier("virtualExecutor") Executor executor, ObjectProvider<IEventService> events) {
        this.executor = executor; this.events = events;
    }

    /**
     * 每 scope 保存最近一百项、最多一千 scope，发布队列最多六十四项；拥塞时只丢监控事件。
     * @param report 脱敏报告
     */
    public void accept(AgentTurnReport report) {
        synchronized (reports) {
            if (!reports.containsKey(report.scope()) && reports.size() >= 1000) { reports.remove(reports.keySet().iterator().next()); }
            var list = reports.computeIfAbsent(report.scope(), ignored -> new ArrayList<>());
            if (list.size() >= 100) { list.removeFirst(); } list.add(report);
        }
        if (!publishing.tryAcquire()) { dropped.increment(); return; }
        try {
            executor.execute(() -> {
                try {
                    var service = events.getIfAvailable();
                    if (service == null) { publishing.release(); return; }
                    service.pushEventWithFuture(new AgentTurnReportEvent(report)).asStage().whenComplete((result, failure) -> {
                        if (failure != null || result == null || !result.isSuccess()) { dropped.increment(); }
                        publishing.release();
                    });
                } catch (RuntimeException failure) { dropped.increment(); publishing.release(); }
            });
        } catch (RuntimeException failure) { dropped.increment(); publishing.release(); }
    }

    /**
     * 独立记录自主动作的真实交付结果，不把模型 SUCCESS 当作已经发言。
     * @param scope 实际作用域
     * @param delivery 发送、放弃、跳过或失败
     */
    public void delivery(AgentScope scope, Delivery delivery) {
        synchronized (deliveries) {
            if (!deliveries.containsKey(scope) && deliveries.size() >= 1000) { deliveries.remove(deliveries.keySet().iterator().next()); }
            deliveries.computeIfAbsent(scope, ignored -> new EnumMap<>(Delivery.class)).merge(delivery, 1L, Long::sum);
        }
    }

    /**
     * 生成当前作用域的中文摘要，不列出其他 scope 或正文。
     * @param scope 当前授权来源
     * @return 最近窗口统计与独立交付计数
     */
    public String describe(AgentScope scope) {
        List<AgentTurnReport> recent; Map<Delivery, Long> sent;
        synchronized (reports) { recent = List.copyOf(reports.getOrDefault(scope, List.of())); }
        synchronized (deliveries) { sent = Map.copyOf(deliveries.getOrDefault(scope, new EnumMap<>(Delivery.class))); }
        return "当前作用域最近轮次：" + recent.size() + "（最多100）\n模型成功：" + recent.stream().filter(r -> r.status() == AgentRunResult.Status.SUCCESS).count()
                + "；其余终止：" + recent.stream().filter(r -> r.status() != AgentRunResult.Status.SUCCESS).count()
                + "\n模型请求：" + recent.stream().mapToLong(AgentTurnReport::modelCalls).sum() + "；工具结果：" + recent.stream().mapToLong(AgentTurnReport::toolCalls).sum()
                + "\n报告 token：" + recent.stream().mapToLong(AgentTurnReport::reportedTokens).sum() + "；估算 token：" + recent.stream().mapToLong(AgentTurnReport::estimatedTokens).sum()
                + "；未知用量次数：" + recent.stream().mapToLong(AgentTurnReport::unknownUsageAttempts).sum()
                + "\n累计轮次耗时毫秒：" + recent.stream().mapToLong(AgentTurnReport::elapsedMillis).sum()
                + "\n自主发送：" + sent.getOrDefault(Delivery.SENT, 0L) + "；放弃：" + sent.getOrDefault(Delivery.ABSTAINED, 0L)
                + "；跳过：" + sent.getOrDefault(Delivery.SKIPPED, 0L) + "；失败或结果不明：" + sent.getOrDefault(Delivery.FAILED, 0L)
                + "\n监控事件全局丢弃/失败计数：" + dropped.sum() + "。模型成功不等于平台已发送；报告与估算不是精确费用。";
    }

    /**
     * 自主业务的真实终态，与模型轮次状态分开。
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Delivery { SENT, ABSTAINED, SKIPPED, FAILED }
}
