package org.zexnocs.teanekoagent.llm.framework.model;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证模型服务的路由、选项优先级与单次调用契约，不启动应用容器。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMModelServiceBoundaryTest {
    private final IBeanScanner scanner = mock(IBeanScanner.class);
    private final ILLMFileConfigService files = mock(ILLMFileConfigService.class);
    private final IDefaultModelResolver defaults = mock(IDefaultModelResolver.class);
    private final ILLMModel model = mock(ILLMModel.class);
    private final LLMModelId route = LLMModelId.of("test-provider");
    private final LLMModelService service = new LLMModelService(scanner, files, defaults);
    private final CompletableFuture<ILLMResult> pending = new CompletableFuture<>();

    /**
     * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
     */
    LLMModelServiceBoundaryTest() {
        var annotation = mock(LLMModel.class);
        when(annotation.id()).thenReturn(route.id());
        when(scanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class))
                .thenReturn(Map.of("model", IndependentPair.of(annotation, model)));
        when(defaults.findDefaultModelId()).thenReturn(Optional.of(route));
        var base = LLMModelOptions.builder().provider(route.id()).model("code-model").temperature(0.4).build();
        when(model.getBaseOptions()).thenReturn(base);
        when(files.getDefaultOptions(eq(route), any())).thenAnswer(invocation -> LLMModelOptions.merge(
                invocation.getArgument(1), LLMModelOptions.builder().model("file-model").maxTokens(2048)
                        .metadata(Map.of("vendor.extension", "from-file")).build()));
        when(model.supports(any())).thenReturn(true);
        when(model.getCapabilities(anyString())).thenReturn(ModelCapabilities.textOnly());
        when(model.call(any(ILLMPrompt.class))).thenReturn(new TaskFuture<>(mock(ILogger.class), "fake-model", pending));
        service.init();
    }

    /**
     * 验证默认路由端口可脱离 Agent 和 Spring 容器注入使用。
     */
    @Test
    void defaultRouteIsInjectedWithoutAgentOrSpringContext() {
        var result = service.call(new LLMPrompt(List.of()));
        assertFalse(result.asStage().toCompletableFuture().isDone());
        verify(defaults).findDefaultModelId();
        var capture = org.mockito.ArgumentCaptor.forClass(ILLMPrompt.class);
        verify(model).call(capture.capture());
        assertEquals("file-model", Objects.requireNonNull(capture.getValue().getOptions()).getModel());
    }

    /**
     * 验证显式路由无需读取默认 Agent 配置。
     */
    @Test
    void explicitRouteDoesNotRequireDefaultConfiguration() {
        when(defaults.findDefaultModelId()).thenThrow(new LLMConfigurationException("Default unavailable."));
        service.call(new LLMPrompt(List.of(), LLMModelOptions.builder().provider(route.id()).build()));
        verifyNoInteractions(defaults);
        verify(model).call(any(ILLMPrompt.class));
    }

    /**
     * 验证文件与请求覆盖保持注册路由和基础参数约定。
     */
    @Test
    void fileAndRequestOverridesPreserveRouteAndBaseOptions() {
        var promptOptions = LLMModelOptions.builder().provider("different-route").model("request-model")
                .maxTokens(512).metadata(Map.of("vendor.request", true)).build();
        service.call(route, new LLMPrompt(List.of(), promptOptions));
        var capture = org.mockito.ArgumentCaptor.forClass(ILLMPrompt.class);
        verify(model).call(capture.capture());
        var effective = capture.getValue().getOptions();
        assertEquals(route.id(), Objects.requireNonNull(effective).getProvider());
        assertEquals("request-model", effective.getModel());
        assertEquals(512, effective.getMaxTokens());
        assertEquals(0.4, effective.getTemperature());
        assertEquals("from-file", effective.getMetadata().get("vendor.extension"));
        assertEquals(true, effective.getMetadata().get("vendor.request"));
        assertEquals("different-route", promptOptions.getProvider());
        assertEquals("code-model", model.getBaseOptions().getModel());
    }

    /**
     * 验证默认路由缺失或未知时不会调用底层模型。
     */
    @Test
    void missingOrUnknownDefaultNeverCallsModel() {
        when(defaults.findDefaultModelId()).thenReturn(Optional.empty());
        assertThrows(LLMConfigurationException.class, () -> service.call(new LLMPrompt(List.of())));
        when(defaults.findDefaultModelId()).thenReturn(Optional.of(LLMModelId.of("unregistered")));
        assertThrows(IllegalArgumentException.class, () -> service.call(new LLMPrompt(List.of())));
        verify(model, never()).call(any(ILLMPrompt.class));
    }

    /**
     * 验证敏感字段或不支持的选项在模型执行前被拒绝。
     */
    @Test
    void secretAndUnsupportedOptionsNeverCallModel() {
        assertThrows(LLMConfigurationException.class, () -> service.call(route,
                new LLMPrompt(List.of(), LLMModelOptions.builder().metadata(Map.of("apiKey", "test-secret")).build())));
        verifyNoInteractions(files);
        when(model.supports(any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.call(route, new LLMPrompt(List.of())));
        verify(model, never()).call(any(ILLMPrompt.class));
    }

    /**
     * 验证单次调用只有一次尝试，异步失败直接传播且不重试。
     */
    @Test
    void callMakesOneAttemptAndPropagatesAsynchronousFailureWithoutRetry() {
        var result = service.call(route, new LLMPrompt(List.of())).asStage().toCompletableFuture();
        assertFalse(result.isDone());
        var failure = new IllegalStateException("fake-provider-failure");
        pending.completeExceptionally(failure);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        verify(model).call(any(ILLMPrompt.class));
    }

    /** 验证预算预检取得真实合并参数且不调用模型，进一步收紧的输出上限能传到实际调用。 */
    @Test void optionPreflightDoesNotCallModelAndPreservesClampedOutput() {
        var prompt = new LLMPrompt(List.of(), LLMModelOptions.builder().maxTokens(512).build());
        var resolved = service.resolveOptions(prompt);
        assertEquals("file-model", resolved.getModel());
        assertEquals(route.id(), resolved.getProvider());
        assertEquals(512, resolved.getMaxTokens());
        verify(model, never()).call(any(ILLMPrompt.class));
        resolved.setMaxTokens(17);
        service.call(new LLMPrompt(List.of(), resolved));
        var capture = org.mockito.ArgumentCaptor.forClass(ILLMPrompt.class);
        verify(model).call(capture.capture());
        assertEquals(17, Objects.requireNonNull(capture.getValue().getOptions()).getMaxTokens());
        assertEquals(512, Objects.requireNonNull(prompt.getOptions()).getMaxTokens());
    }
}
