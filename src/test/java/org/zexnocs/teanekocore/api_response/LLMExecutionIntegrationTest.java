package org.zexnocs.teanekocore.api_response;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.model.*;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatModel;
import org.zexnocs.teanekoagent.llm.instance.kimi.KimiChatModel;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionsModel;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesModel;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestTimeoutException;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * LLM → 真实 APIResponseService → WebClient → 受控传输，不启动 Spring 或外部 HTTP。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMExecutionIntegrationTest {
    private static final List<BiFunction<IAPIResponseService, ICredentialResolver, AbstractLLMModel>> MODELS = List.of(
            DeepSeekChatModel::new, OpenAIResponsesModel::new, KimiChatModel::new, OpenAIChatCompletionsModel::new);

    /**
     * 验证所有供应商适配器传递执行策略并映射单次响应。
     */
    @Test
    void allProviderAdaptersUseThePolicyAndMapTheSingleResponse() {
        for (var factory : MODELS) {
            var fixture = new APIResponseExecutionTest.Fixture(Schedulers.immediate());
            try (var control = ExecutionControl.create()) {
                var model = factory.apply(fixture.service, provider -> new LLMProviderAccess("fake-key", null, null));
                var result = model.call(prompt(), ExecutionPolicy.controlled(Duration.ofSeconds(4), control)).asStage().toCompletableFuture();
                assertFalse(result.isDone());
                assertEquals(1, fixture.requests.size());
                assertEquals("Bearer fake-key", fixture.requests.getFirst().headers().getFirst("Authorization"));
                fixture.respondJson(replyJson(model.getProvider()));
                var message = result.join().getFirstMessage().orElseThrow();
                assertEquals("answer", ((TextLLMContentPart) message.getContents().getFirst().getContentPart()).getText());
                assertEquals(0, fixture.timer.pendingTasks());
                assertDoesNotThrow(control::checkActive);
            }
        }
    }

    /**
     * 验证供应商阶段超时释放 HTTP，既不暗中重试也不停止父执行。
     */
    @Test
    void allProviderTimeoutsReleaseHttpAndDoNotRetryOrStopTheSharedExecution() {
        for (var factory : MODELS) {
            var fixture = new APIResponseExecutionTest.Fixture(Schedulers.immediate());
            try (var control = ExecutionControl.create()) {
                var model = factory.apply(fixture.service, provider -> new LLMProviderAccess("fake-key", null, null));
                var result = model.call(prompt(), ExecutionPolicy.controlled(Duration.ofSeconds(1), control)).asStage().toCompletableFuture();
                fixture.timer.advance(Duration.ofSeconds(1));
                assertInstanceOf(APIRequestTimeoutException.class, assertThrows(CompletionException.class, result::join).getCause());
                assertEquals(1, fixture.cancelled.get());
                assertEquals(1, fixture.requests.size());
                assertDoesNotThrow(control::checkActive);
                fixture.respondJson(replyJson(model.getProvider()));
                assertTrue(result.isCompletedExceptionally());
            }
        }
    }

    /**
     * 验证供应商取消信号到达真实 HTTP 订阅。
     */
    @Test
    void allProviderCancellationsReachTheActualHttpSubscription() {
        for (var factory : MODELS) {
            var fixture = new APIResponseExecutionTest.Fixture(Schedulers.immediate());
            try (var control = ExecutionControl.create()) {
                var model = factory.apply(fixture.service, provider -> new LLMProviderAccess("fake-key", null, null));
                var result = model.call(prompt(), ExecutionPolicy.controlled(Duration.ofSeconds(4), control)).asStage().toCompletableFuture();
                control.cancel();
                var stopped = assertInstanceOf(ExecutionStoppedException.class, assertThrows(CompletionException.class, result::join).getCause());
                assertEquals(ExecutionControl.StopReason.CANCELLED, stopped.reason());
                assertEquals(1, fixture.cancelled.get());
                assertEquals(0, fixture.timer.pendingTasks());
            }
        }
    }

    /**
     * 验证模型服务经过路由与选项解析仍保留原执行策略。
     */
    @Test
    void modelServicePreservesThePolicyThroughRouteAndOptionsResolution() {
        var fixture = new APIResponseExecutionTest.Fixture(Schedulers.immediate());
        var model = new DeepSeekChatModel(fixture.service, provider -> new LLMProviderAccess("fake-key", null, null));
        var files = mock(ILLMFileConfigService.class);
        when(files.getDefaultOptions(any(), any())).thenAnswer(invocation -> LLMModelOptions.merge(invocation.getArgument(1),
                LLMModelOptions.builder().model("file-model").build()));
        var scanner = mock(IBeanScanner.class);
        when(scanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class))
                .thenReturn(Map.of("model", IndependentPair.of(DeepSeekChatModel.class.getAnnotation(LLMModel.class), model)));
        var service = new LLMModelService(scanner, files, () -> Optional.of(LLMModelId.of("deepseek")));
        service.init();
        try (var control = ExecutionControl.create()) {
            var options = LLMModelOptions.builder().model("request-model").metadata(Map.of("vendor.flag", "value")).build();
            var input = new LLMPrompt(prompt().getMessages(), options);
            var result = service.call(input, ExecutionPolicy.controlled(Duration.ofSeconds(2), control)).asStage().toCompletableFuture();
            control.cancel();
            assertInstanceOf(ExecutionStoppedException.class, assertThrows(CompletionException.class, result::join).getCause());
            assertEquals(1, fixture.cancelled.get());
            assertEquals("request-model", options.getModel());
            assertEquals(Map.of("vendor.flag", "value"), options.getMetadata());
            verify(files).getDefaultOptions(eq(LLMModelId.of("deepseek")), any());
        }
    }

    /**
     * 验证预先取消的调用在路由、凭据与 HTTP 之前被拒绝。
     */
    @Test
    void preCancelledCallsRejectBeforeRoutingCredentialsOrHttp() {
        var api = mock(IAPIResponseService.class);
        var credentials = mock(ICredentialResolver.class);
        try (var control = ExecutionControl.create()) {
            control.cancel();
            var policy = ExecutionPolicy.controlled(Duration.ofSeconds(2), control);
            for (var factory : MODELS) {
                assertThrows(ExecutionStoppedException.class, () -> factory.apply(api, credentials).call(prompt(), policy));
            }
            var files = mock(ILLMFileConfigService.class);
            var defaults = mock(IDefaultModelResolver.class);
            var scanner = mock(IBeanScanner.class);
            var service = new LLMModelService(scanner, files, defaults);
            assertThrows(ExecutionStoppedException.class, () -> service.call(prompt(), policy));
            verifyNoInteractions(api, credentials, files, defaults, scanner);
        }
    }

    /**
     * 验证凭据解析期间取消保留停止原因且不发起 HTTP。
     */
    @Test
    void cancellationDuringCredentialResolutionKeepsItsReasonAndDoesNotSubmitHttp() {
        for (var factory : MODELS) {
            var fixture = new APIResponseExecutionTest.Fixture(Schedulers.immediate());
            try (var control = ExecutionControl.create()) {
                var model = factory.apply(fixture.service, provider -> {
                    control.cancel();
                    return new LLMProviderAccess("fake-key", null, null);
                });
                var error = assertThrows(ExecutionStoppedException.class,
                        () -> model.call(prompt(), ExecutionPolicy.controlled(Duration.ofSeconds(2), control)));
                assertEquals(ExecutionControl.StopReason.CANCELLED, error.reason());
                assertTrue(fixture.requests.isEmpty());
            }
        }
    }

    /**
     * 验证未实现新策略的接口明确拒绝而非静默忽略。
     */
    @Test
    void unadaptedInterfacesRejectExecutionPoliciesRatherThanIgnoringThem() {
        var legacyCalls = new AtomicInteger();
        ILLMModel model = new ILLMModel() {
            /**
             * 提供旧接口夹具的固定供应商标识。
             *
             * @return 按本方法契约生成的文本或标识
             */
            @Override public String getProvider() { return "legacy"; }
            /**
             * 提供旧接口夹具的固定模型名称。
             *
             * @return 按本方法契约生成的文本或标识
             */
            @Override public String getModel() { return "legacy"; }
            /**
             * 记录旧模型入口是否被调用；策略重载未适配时应在进入此方法前拒绝。
             *
             * @param prompt 经过宿主装配的消息与本次模型选项
             * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
             */
            @Override public TaskFuture<ILLMResult> call(ILLMPrompt prompt) { legacyCalls.incrementAndGet(); return null; }
        };
        IAPIResponseService api = new IAPIResponseService() {
            /**
             * 记录旧 HTTP 入口是否被调用，验证新策略不会被静默忽略。
             *
             * @param request 本次 HTTP 请求数据
             * @param responseType 响应反序列化类型
             * @param disableCache 是否禁止本次响应写入缓存
             * @param skipCache 是否跳过缓存读取
             * @param <REQ> 请求数据类型
             * @param <RES> 响应数据类型
             * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
             */
            @Override
            public <REQ extends IAPIRequestData, RES extends IAPIResponseData> TaskFuture<RES> addTask(
                    REQ request, Class<RES> responseType, boolean disableCache, boolean skipCache) {
                legacyCalls.incrementAndGet(); return null;
            }
        };
        assertThrows(UnsupportedOperationException.class, () -> model.call(prompt(), ExecutionPolicy.defaults()));
        assertThrows(UnsupportedOperationException.class, () -> api.addTask(new APIResponseExecutionTest.Query("one"),
                APIResponseExecutionTest.Reply.class, true, true, ExecutionPolicy.defaults()));
        assertEquals(0, legacyCalls.get());
    }

    /**
     * 构造不含凭据的最小文本 Prompt，供真实适配器与受控 HTTP 集成验证。
     *
     * @return 满足本方法约束的 {@code LLMPrompt} 结果
     */
    private static LLMPrompt prompt() {
        return new LLMPrompt(LLMUserMessage.builder().contents(LLMContentListBuilder.builder().addText("hello").build()).build());
    }

    /**
     * 按供应商返回对应固定响应结构，不调用真实模型。
     *
     * @param provider 供应商注册标识，不由模型名称推断
     * @return 按本方法契约生成的文本或标识
     */
    private static String replyJson(String provider) {
        return provider.equals("openai")
                ? "{\"id\":\"r\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"answer\"}]}]}"
                : "{\"id\":\"r\",\"choices\":[{\"index\":0,\"message\":{\"content\":\"answer\"},\"finish_reason\":\"stop\"}]}";
    }
}
