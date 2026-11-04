package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.component.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.component.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 不启动容器、数据库或 HTTP，验证就绪检查的配置隔离、快照及敏感信息边界。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：检查就绪失败的阶段诊断，继续验证凭据脱敏与恢复。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
class AgentReadinessServiceTest {
    private static final String SECRET = "offline-sensitive-sentinel";
    private final IAgentRuntimeConfigPort config = mock(IAgentRuntimeConfigPort.class);
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final ILLMModel model = mock(ILLMModel.class);
    private final ICredentialResolver credentials = mock(ICredentialResolver.class);
    private final ILogger logger = mock(ILogger.class);
    private final AgentRuntimeConfig settings = new AgentRuntimeConfig(true, LLMInteractionSettings.defaults());
    private final LLMModelOptions options = LLMModelOptions.builder().provider("test").model("local-model").maxTokens(100).build();
    private final AgentDefinition definition = new AgentDefinition("test", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
    private final AgentReadinessService service = new AgentReadinessService(config, models, credentials, logger);

    /** 注入固定有效配置和凭据，替身端口不发送任何真实请求。 */
    AgentReadinessServiceTest() {
        when(config.read()).thenReturn(settings);
        when(models.resolveOptions(any())).thenReturn(options);
        when(models.getModel("test")).thenReturn(model);
        when(model.getCapabilities("local-model")).thenReturn(ModelCapabilities.textOnly());
        when(credentials.require("test")).thenReturn(new LLMProviderAccess(SECRET, null, null));
    }

    /** 验证就绪结果只有固定模型数据，没有凭据或运行句柄，也不调用模型。 */
    @Test void readySnapshotHasNoCredentialAndDoesNotCallModel() {
        var nested = new ArrayList<Object>(List.of("原值"));
        options.setMetadata(Map.of("state", nested));
        var ready = service.inspect(definition);
        assertEquals(AgentReadiness.Status.READY, ready.status());
        var snapshot = ready.snapshot().orElseThrow();
        options.setModel("被修改"); nested.set(0, "被修改");
        assertEquals("local-model", snapshot.modelOptions().toOptions().getModel());
        assertEquals(List.of("原值"), snapshot.modelOptions().toOptions().getMetadata().get("state"));
        assertEquals(settings.interaction(), snapshot.interaction());
        assertFalse(ready.toString().contains(SECRET));
        verify(models, never()).call(any(ILLMPrompt.class));
        verify(models, never()).call(any(ILLMPrompt.class), any(ExecutionPolicy.class));
        verify(model, never()).call(any(ILLMPrompt.class));
        verify(credentials).require("test");
    }

    /** 验证构造阶段不访问配置、模型、凭据或日志，不因未部署设置阻断 Bean 创建。 */
    @Test void constructionDoesNotResolveConfiguration() {
        var local = mock(IAgentRuntimeConfigPort.class);
        var modelPort = mock(ILLMModelService.class);
        var credentialPort = mock(ICredentialResolver.class);
        var log = mock(ILogger.class);
        assertDoesNotThrow(() -> new AgentReadinessService(local, modelPort, credentialPort, log));
        verifyNoInteractions(local, modelPort, credentialPort, log);
    }

    /** 验证本地总开关关闭时不继续读取模型或凭据。 */
    @Test void localDisableStopsFurtherChecks() {
        when(config.read()).thenReturn(new AgentRuntimeConfig(false, LLMInteractionSettings.defaults()));
        var ready = service.inspect(definition);
        assertEquals(AgentReadiness.Status.LOCAL_DISABLED, ready.status());
        assertTrue(ready.snapshot().isEmpty());
        verifyNoInteractions(models, model, credentials, logger);
    }

    /** 2026/11/04：主配置失败报告安全阶段，并可在配置重载后重新检查恢复。 */
    @Test
    void localConfigurationFailureIsRedactedAndRecoverable() {
        when(config.read()).thenThrow(new IllegalStateException(SECRET)).thenReturn(settings);
        var failed = service.inspect(definition);
        assertEquals(AgentReadiness.Status.LOCAL_CONFIG_UNAVAILABLE, failed.status());
        assertFalse(failed.toString().contains(SECRET));
        // 2026/11/04：诊断包含固定阶段，不依赖供应商或配置异常原文。
        verify(logger).error(eq(AgentReadinessService.class.getName()), startsWith(AgentReadiness.Status.LOCAL_CONFIG_UNAVAILABLE.description()));
        assertEquals(AgentReadiness.Status.READY, service.inspect(definition).status());
    }

    /** 2026/11/04：模型参数失败报告检查位置，记忆整理区分配置拒绝和运行中撤权。 */
    @Test
    void modelFailureDoesNotReachCredentialBoundary() {
        when(models.resolveOptions(any())).thenThrow(new IllegalStateException(SECRET));
        var failed = service.inspect(definition);
        assertEquals(AgentReadiness.Status.MODEL_UNAVAILABLE, failed.status());
        assertFalse(failed.toString().contains(SECRET));
        verifyNoInteractions(credentials);
        var decision = new AgentAccessDecision(AgentAccessDecision.Reason.READINESS_UNAVAILABLE,
                java.util.Optional.empty(), java.util.Optional.of(failed));
        assertEquals(org.zexnocs.teanekoagent.component.memory.layered.MemoryProcessingException.Reason.READINESS_UNAVAILABLE,
                new org.zexnocs.teanekoagent.component.memory.layered.MemoryProcessingException(decision).reason());
        verify(logger).error(eq(AgentReadinessService.class.getName()), argThat(message ->
                message.contains("解析有效模型参数") && message.contains("context-window-tokens") && !message.contains(SECRET)));
        verify(logger, never()).error(anyString(), anyString(), any(Throwable.class));
    }

    /** 2026/11/04：缺失认证的阶段诊断与其他配置失败分开，始终不回显凭据值。 */
    @Test
    void credentialFailureIsDistinctAndRedacted() {
        when(credentials.require("test")).thenThrow(new IllegalStateException(SECRET));
        var failed = service.inspect(definition);
        assertEquals(AgentReadiness.Status.CREDENTIALS_UNAVAILABLE, failed.status());
        assertFalse(failed.toString().contains(SECRET));
        verify(logger).error(eq(AgentReadinessService.class.getName()), startsWith(AgentReadiness.Status.CREDENTIALS_UNAVAILABLE.description()));
        verify(logger, never()).error(anyString(), anyString(), any(Throwable.class));
    }

    /** 验证敏感 metadata 或缺失输出上限不会进入组件或被视为已就绪。 */
    @Test void malformedResolvedOptionsCannotProduceSnapshot() {
        options.setMetadata(Map.of("api-key", SECRET));
        assertEquals(AgentReadiness.Status.MODEL_UNAVAILABLE, service.inspect(definition).status());
        options.setMetadata(Map.of()); options.setMaxTokens(null);
        assertEquals(AgentReadiness.Status.MODEL_UNAVAILABLE, service.inspect(definition).status());
        verifyNoInteractions(credentials);
    }

    /**
     * 验证一个定义失败不将其他有效定义污染成全局关闭状态。
     * 2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
     */
    @Test void definitionFailureDoesNotDisableOtherAgents() {
        when(models.resolveOptions(any())).thenAnswer(invocation -> {
            ILLMPrompt prompt = invocation.getArgument(0);
            if (Objects.requireNonNull(prompt.getOptions()).findProvider().filter("broken"::equals).isPresent()) {
                throw new IllegalStateException(SECRET);
            }
            return options;
        });
        var broken = new AgentDefinition("broken", new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("broken").build()),
                Set.of(AgentActivity.SERVICE));
        assertEquals(AgentReadiness.Status.MODEL_UNAVAILABLE, service.inspect(broken).status());
        assertEquals(AgentReadiness.Status.READY, service.inspect(definition).status());
    }

    /** 验证诊断端口自身故障不会把配置失败变成调用线程异常。 */
    @Test void diagnosticFailureCannotEscapeReadinessResult() {
        when(config.read()).thenThrow(new IllegalStateException(SECRET));
        doThrow(new IllegalStateException("日志不可用")).when(logger).error(anyString(), anyString());
        assertEquals(AgentReadiness.Status.LOCAL_CONFIG_UNAVAILABLE, service.inspect(definition).status());
        verifyNoInteractions(models, credentials);
    }
}
