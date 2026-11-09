package org.zexnocs.teanekoagent.runtime;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig;
import org.zexnocs.teanekoagent.integration.monitor.AgentObservationService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMRequestSnapshot;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.runtime.monitor.AgentActivityTimeline;
import org.zexnocs.teanekoagent.runtime.monitor.AgentTurnReport;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.ILogger;

import java.sql.SQLSyntaxErrorException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 观测开关实时读取，异常快照不破坏业务类别和资源控制。
 * <br>2026/11/06：不用真实模型或配置目录。
 * <br>2026/11/06：按独立开关和日志级别输出精简文字汇总，去重并继续脱敏。
 *
 * <br>2026/11/08：INFO 与 DEBUG 内容分离，预览受长度及后端资格限制。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
class AgentObservationTest {
    /** INFO 只有单行统计，正文仅进入受限 DEBUG，原字段开关仍可关闭预览。 */
    @Test
    void infoSummaryDoesNotContainApplicationPayload() {
        var files = mock(IFileConfigService.class);
        var logger = mock(ILogger.class);
        var config = new AgentObservationFileConfig();
        config.setDiagnosticLog(false);
        when(logger.isDebugEnabled()).thenReturn(true);
        when(files.requireCurrent(AgentObservationFileConfig.class)).thenReturn(config);
        var message = LLMUserMessage.builder().contents(new LLMContentListBuilder()
                .addText("后台学习内容".repeat(10000)).build()).build();
        var input = new LLMRequestSnapshot(List.of(message), "{}");
        var attempt = new LLMInteractionResult.ModelAttempt(1, Optional.empty(), Optional.empty(), Optional.of(input));
        var result = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(message), null,
                List.of(attempt), List.of(), Map.of());
        var service = new AgentObservationService(files, logger);
        service.finished(UUID.randomUUID(), 12, result);
        var summary = ArgumentCaptor.forClass(String.class);
        verify(logger).info(eq("LLM 请求汇总"), summary.capture());
        assertFalse(summary.getValue().contains("后台学习内容"));
        assertFalse(summary.getValue().contains("\n"));
        var preview = ArgumentCaptor.forClass(String.class);
        verify(logger).debug(eq("LLM 请求汇总预览"), preview.capture());
        assertTrue(preview.getValue().contains("预览截断"));
        assertTrue(preview.getValue().length() < 2200);
        clearInvocations(logger);
        when(logger.isDebugEnabled()).thenReturn(false);
        service.finished(UUID.randomUUID(), 12, result);
        verify(logger, never()).debug(eq("LLM 请求汇总预览"), anyString());
        clearInvocations(logger);
        when(logger.isDebugEnabled()).thenReturn(true);
        config.setIncludeContext(false);
        service.finished(UUID.randomUUID(), 12, result);
        verify(logger, never()).debug(eq("LLM 请求汇总预览"), anyString());
    }

    /** 2026/11/06：DEBUG 和 NONE 热更新不再产生 INFO，关闭后不构造正文。 */
    @Test
    void visibilitySelectsExactlyOneLevel() {
        var files = mock(IFileConfigService.class);
        var logger = mock(ILogger.class);
        var config = new AgentObservationFileConfig();
        when(files.requireCurrent(AgentObservationFileConfig.class)).thenReturn(config);
        var service = new AgentObservationService(files, logger);
        var result = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(), null, List.of(), List.of(), Map.of());
        config.setVisibility(AgentObservationFileConfig.Visibility.DEBUG);
        service.finished(UUID.randomUUID(), 10, result);
        verify(logger).debug(eq("LLM 请求汇总"), contains("状态=SUCCESS"));
        verify(logger, never()).info(anyString(), anyString());
        clearInvocations(logger);
        config.setVisibility(AgentObservationFileConfig.Visibility.NONE);
        service.finished(UUID.randomUUID(), 10, result);
        verifyNoInteractions(logger);
    }
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
