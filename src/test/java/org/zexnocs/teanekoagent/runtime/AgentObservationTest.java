package org.zexnocs.teanekoagent.runtime;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig;
import org.zexnocs.teanekoagent.integration.monitor.AgentObservationService;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.runtime.monitor.AgentTurnReport;
import org.zexnocs.teanekoagent.runtime.monitor.AgentActivityTimeline;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.ILogger;
import java.sql.SQLSyntaxErrorException;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 观测开关实时读取，异常快照不破坏业务类别和资源控制。
 * <br>2026/11/06：不用真实模型或配置目录。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.5-l
 */
class AgentObservationTest {
    /** 热更新各自关闭节点日志，损坏配置和日志端口失败不能影响调用者。 */
    @Test
    void hotSwitchesObservationWithoutPropagatingLoggerFailure() {
        var files = mock(IFileConfigService.class);
        var logger = mock(ILogger.class);
        var config = new AgentObservationFileConfig();
        when(files.requireCurrent(AgentObservationFileConfig.class)).thenReturn(config);
        var service = new AgentObservationService(files, logger);
        var id = UUID.randomUUID();
        var result = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(), null, List.of(), List.of(), Map.of());
        var report = new AgentTurnReport(id, EntityId.random(), new AgentScope("web", "test"), AgentActivity.COMMAND,
                AgentRunResult.Status.SUCCESS, 12, 0, 0, 0, 0, 0);
        service.finished(id, 12, result);
        verify(logger).info(eq("LLM 请求汇总"), contains(id.toString()));
        config.setLlmInfo(false);
        config.setAgentInfo(false);
        clearInvocations(logger);
        service.finished(id, 12, result);
        service.finished(report, null);
        verifyNoInteractions(logger);
        config.setAgentInfo(true);
        doThrow(new IllegalStateException("日志故障")).when(logger).info(anyString(), anyString());
        assertDoesNotThrow(() -> service.finished(report, null));
        when(files.requireCurrent(AgentObservationFileConfig.class)).thenThrow(new IllegalStateException("配置故障"));
        assertDoesNotThrow(() -> service.finished(id, 12, result));
        service.finished(id, 12, result);
        verify(logger, times(1)).warn(eq("Agent 观测"), anyString());
    }

    /** 2026/11/06：任意阶段数只改变网页时间线，最终只产生一个 INFO。 */
    @Test
    void timelineStagesDoNotProduceConsoleInfo() {
        var files = mock(IFileConfigService.class);
        var logger = mock(ILogger.class);
        when(files.requireCurrent(AgentObservationFileConfig.class)).thenReturn(new AgentObservationFileConfig());
        var timeline = new AgentActivityTimeline();
        timeline.installObservation(new AgentObservationService(files, logger));
        var request = mock(AgentRunRequest.class);
        var actor = mock(AgentActorContext.class);
        var scope = new AgentScope("web", "test");
        var id = UUID.randomUUID();
        when(request.requestId()).thenReturn(id);
        when(request.actor()).thenReturn(actor);
        when(actor.scope()).thenReturn(scope);
        when(request.activity()).thenReturn(AgentActivity.COMMAND);
        for (int index = 0; index < 20; index++) {
            timeline.mark(request, "agent.phase." + index, "RETURNED");
        }
        verifyNoInteractions(logger);
        timeline.finish(new AgentTurnReport(id, EntityId.random(), scope, AgentActivity.COMMAND,
                AgentRunResult.Status.SUCCESS, 12, 0, 0, 0, 0, 0));
        verify(logger, times(1)).info(eq("Agent 请求汇总"), contains(id.toString()));
        assertEquals(21, timeline.page(scope, 0, 100).events().size());
    }

    /** 资料边界保留业务原因及原始安全位置，不公开 SQL 正文。 */
    @Test
    void promptBoundaryKeepsOriginalFailureLocation() {
        var source = new SQLSyntaxErrorException("不公开的 SQL 正文", "42000", 1064);
        var safe = new AgentPromptException(AgentPromptException.Reason.UNAVAILABLE, source);
        assertNull(safe.getCause());
        assertEquals(AgentPromptException.Reason.UNAVAILABLE, safe.reason());
        assertEquals(FailureDiagnostic.from(source).location(), FailureDiagnostic.from(safe).location());
        assertTrue(safe.failureReason().contains("42000"));
        assertFalse(safe.failureReason().contains("SQL 正文"));
    }
}
