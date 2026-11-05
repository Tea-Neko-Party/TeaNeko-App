package org.zexnocs.teanekoagent.runtime.monitor;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.core.env.Environment;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在原异步监控链中处理有界用量阈值和冷却，不让告警成为主业务依赖。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：用量告警给出请求号、报告量、估算量、阈值与模型次数，保留原冷却策略。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.3-l
 */
@EventListener
public final class AgentTokenAlerts {
    private final Environment environment;
    private final IEventService events;
    private final ILogger logger;
    private final Map<String, State> states = new LinkedHashMap<>();
    /**
     * 借用本地配置与原事件/日志，不创建定时器。
     * @param environment 本地阈值、开关和冷却
     * @param events 原监控事件链
     * @param logger 固定脱敏日志
     */
    public AgentTokenAlerts(Environment environment, IEventService events, ILogger logger) {
        this.environment = environment; this.events = events; this.logger = logger;
    }
    /**
     * 同 scope/实例独立冷却，至多保留一千个聚合状态；估算不冒充供应商计费。
     * 2026/11/05：只增加可核对的计量信息，不降低模型输出额度或改变告警阈值。
     * @param event 当前结束轮次的不可变报告
     */
    @EventHandler public void onReport(AgentTurnReportEvent event) {
        try {
            String prefix = "tea-neko.agent.monitor.";
            if (!environment.getProperty(prefix + "alerts-enabled", Boolean.class, true)) { return; }
            long threshold = environment.getProperty(prefix + "alert-tokens", Long.class, 16_384L);
            long cooldown = environment.getProperty(prefix + "alert-cooldown-seconds", Long.class, 300L);
            if (threshold < 1 || cooldown < 1 || cooldown > 86400) { throw new IllegalArgumentException("Agent 告警阈值或冷却无效。"); }
            var report = event.getData();
            if (Math.addExact(report.reportedTokens(), report.estimatedTokens()) < threshold) { return; }
            var key = report.scope().clientId() + ":" + report.scope().scopeId() + ":" + report.agentId();
            long suppressed; var now = Instant.now();
            synchronized (states) {
                var previous = states.get(key);
                if (previous != null && previous.until().isAfter(now)) {
                    states.put(key, new State(previous.until(), Math.addExact(previous.suppressed(), 1))); return;
                }
                suppressed = previous == null ? 0 : previous.suppressed();
                if (previous == null && states.size() >= 1000) { states.remove(states.keySet().iterator().next()); }
                states.put(key, new State(now.plusSeconds(cooldown), 0));
            }
            logger.warn("Agent 用量告警", "单轮 token 用量达到本地阈值。请求=" + report.requestId()
                    + "，供应商报告=" + report.reportedTokens() + "，估算=" + report.estimatedTokens()
                    + "，阈值=" + threshold + "，模型调用=" + report.modelCalls()
                    + "，未知用量调用=" + report.unknownUsageAttempts()
                    + "；这是用量提醒，不代表请求失败或精确费用。冷却期间按实例和作用域合并。");
            events.pushEvent(new AgentTokenAlertEvent(new AgentTokenAlertEvent.Alert(report, threshold, suppressed)));
        } catch (RuntimeException unavailable) { /* 告警配置或监听故障不能改变原轮次结果。 */ }
    }
    /**
     * 不保存 Prompt 的进程内冷却状态。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param until 下次可告警时刻
     * @param suppressed 当前合并次数
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    private record State(Instant until, long suppressed) { }
}
