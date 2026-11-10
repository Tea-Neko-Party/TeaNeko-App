package org.zexnocs.teanekoagent.runtime.monitor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证累计用量提醒使用监控配置，记录可操作的来源并接受显式阈值覆盖。
 * <br>2026/11/10：复现三次模型调用累计 38397 token 的默认告警和 163840 阈值覆盖。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
class AgentTokenAlertsTest {
    /** 缺省提醒和管理员覆盖均只消费已结束轮次，日志明确累计口径与配置键。 */
    @Test
    void accumulatedUsageUsesTheMonitoringThresholdAndReportsItsSource() {
        var report = new AgentTurnReportEvent(new AgentTurnReport(UUID.randomUUID(), EntityId.random(),
                new AgentScope("test", "scope"), AgentActivity.AUTOMATIC_REPLY, AgentRunResult.Status.SUCCESS,
                34169, 3, 2, 38397, 0, 0));
        var events = mock(IEventService.class);
        var logger = mock(ILogger.class);
        new AgentTokenAlerts(new MockEnvironment(), events, logger).onReport(report);
        var message = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(eq("Agent 用量告警"), message.capture());
        verify(events).pushEvent(any(AgentTokenAlertEvent.class));
        assertTrue(message.getValue().contains("阈值=16384"));
        assertTrue(message.getValue().contains("阈值配置=tea-neko.agent.monitor.alert-tokens"));
        assertTrue(message.getValue().contains("本轮全部模型调用的总用量（含输入及输出）"));
        assertTrue(message.getValue().contains("不覆盖每次模型的 max-tokens"));

        var configuredEvents = mock(IEventService.class);
        var configuredLogger = mock(ILogger.class);
        var configuration = new MockEnvironment().withProperty("tea-neko.agent.monitor.alert-tokens", "163840");
        new AgentTokenAlerts(configuration, configuredEvents, configuredLogger).onReport(report);
        verifyNoInteractions(configuredEvents, configuredLogger);
    }
}
