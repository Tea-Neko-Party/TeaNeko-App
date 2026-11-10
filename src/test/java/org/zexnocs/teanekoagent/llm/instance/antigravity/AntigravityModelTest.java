package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.zexnocs.teanekoagent.llm.configuration.FileLLMCredentialResolver;
import org.zexnocs.teanekoagent.llm.file_config.LLMFileConfigService;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekoagent.llm.file_config.form.LLMConfigurationAdapter;
import org.zexnocs.teanekoagent.llm.file_config.form.LLMConfigurationDirectory;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelService;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.APIResponseService;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 使用真实文件路由和模型目录验证原生适配器，HTTP 保持合成可控。
 * <br>2026/11/10：验证注册、凭据隔离、原模型额度、工具交互、目录发现及执行取消边界。
 * <br>2026/11/10：增加本地真实 SSE 工具续轮，原非流式路径仍可显式选择。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Timeout(10)
class AntigravityModelTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 本地原生 HTTP 完整聚合后才执行工具，工具续轮保留签名并使用同一个受控交互。
     * @throws Exception 当本地服务或有界异步等待失败时
     */
    @Test
    void streamedToolRoundWaitsForCompleteResponse() throws Exception {
        var requests = new AtomicInteger();
        var effects = new AtomicInteger();
        var firstPart = new CompletableFuture<Void>();
        var release = Sinks.<String>one();
        var server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) ->
                request.receive().aggregate().asString().flatMap(body -> {
                    assertEquals("/antigravity/v1beta/models/gemini-3.8-flash-medium:streamGenerateContent?alt=sse",
                            request.uri());
                    assertEquals("synthetic-secret", request.requestHeaders().get("x-goog-api-key"));
                    var input = mapper.readTree(body);
                    assertEquals(163840, input.at("/generationConfig/maxOutputTokens").asInt());
                    response.header("Content-Type", "text/event-stream");
                    if (requests.incrementAndGet() == 1) {
                        return response.sendString(Flux.just("""
                                data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"work"},"thoughtSignature":"opaque"}]}}]}

                                """).concatWith(Mono.defer(() -> {
                                    firstPart.complete(null);
                                    return release.asMono();
                                }))).then();
                    }
                    assertEquals("opaque", input.at("/contents/1/parts/0/thoughtSignature").asString());
                    assertEquals("实际回执", input.at("/contents/2/parts/0/functionResponse/response/result").asString());
                    return response.sendString(Mono.just("""
                            data: {"candidates":[{"content":{"parts":[{"text":"已完成"}]},"finishReason":"STOP"}]}

                            """)).then();
                })).bindNow(Duration.ofSeconds(3));
        try (var scheduler = Executors.newSingleThreadScheduledExecutor()) {
            var entry = entry();
            entry.setStream(null);
            entry.setBaseUrl("http://127.0.0.1:" + server.port());
            var api = new APIResponseService(Schedulers.immediate(), mock(ICacheService.class), mock(ILogger.class));
            var settings = LLMInteractionSettings.defaults();
            var interaction = new LLMInteractionService(service(api, files(entry)), () -> settings,
                    Runnable::run, scheduler);
            var tools = new LLMToolSet(mapper, List.of(new LLMToolBinding(
                    LLMTool.builder().name("work").description("本地合成工具").build(), (call, context) -> {
                        effects.incrementAndGet();
                        return CompletableFuture.completedStage("实际回执");
                    })));
            try (var parent = ExecutionControl.withDeadline(settings.limits().newBudget(),
                    Clock.systemUTC().instant().plusSeconds(6), Clock.systemUTC(), scheduler)) {
                var context = new ToolInvocationContext(UUID.randomUUID(),
                        EntitySnapshot.of(EntityId.random(), ComponentPatch.empty()), parent);
                var pending = interaction.interact(new LLMPrompt(user(),
                        LLMModelOptions.builder().provider("gemini").build()), tools, context).toCompletableFuture();
                firstPart.get(3, TimeUnit.SECONDS);
                assertFalse(pending.isDone());
                assertEquals(0, effects.get());
                release.tryEmitValue("data: {\"candidates\":[{\"finishReason\":\"STOP\"}]}\n\n").orThrow();
                var result = pending.get(4, TimeUnit.SECONDS);
                assertEquals(LLMInteractionResult.Status.SUCCESS, result.status(), () -> String.valueOf(result.failure()));
                assertEquals(2, requests.get());
                assertEquals(1, effects.get());
            }
        } finally {
            release.tryEmitEmpty();
            server.disposeNow(Duration.ofSeconds(3));
        }
    }

    /** @return 无业务数据的统一消息。 */
    private LLMUserMessage user() {
        return LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("合成测试").build()).build();
    }

    /** 保留 gemini 路由 ID 后，文件适配归属、凭据和请求路径仍通过原注册目录工作。 */
    @Test
    void registeredRouteKeepsItsOwnCredentialsAndOutputLimit() {
        var entry = entry();
        var files = files(entry);
        var api = mock(IAPIResponseService.class);
        var response = mapper.readValue("""
                {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"text":"OK"}]}}]}
                """, AntigravityResponseData.class);
        when(api.addTask(any(), eq(AntigravityResponseData.class), eq(true), eq(true)))
                .thenReturn(new TaskFuture<>(mock(ILogger.class), "原生离线传输", CompletableFuture.completedFuture(response)));
        var service = service(api, files);
        var result = service.call(new LLMPrompt(user(), LLMModelOptions.builder().provider("gemini").build()));
        assertEquals("stop", result.asStage().toCompletableFuture().join().getChoices().getFirst().getFinishReason());
        var capture = ArgumentCaptor.forClass(IAPIRequestData.class);
        verify(api).addTask(capture.capture(), eq(AntigravityResponseData.class), eq(true), eq(true));
        var request = capture.getValue();
        assertEquals("/antigravity/v1beta/models/gemini-3.8-flash-medium:generateContent", request.getPathOverride());
        assertEquals("synthetic-secret", request.headers().get("x-goog-api-key"));
        assertEquals(163840, mapper.valueToTree(request).at("/generationConfig/maxOutputTokens").asInt());
        assertFalse(mapper.writeValueAsString(request).contains("synthetic-secret"));
        entry.setAdapter("openai");
        var credentials = new FileLLMCredentialResolver(files);
        assertThrows(LLMConfigurationException.class, () -> credentials.require(AntigravityModel.PROVIDER,
                LLMModelOptions.builder().provider("gemini").build()));
        verifyNoMoreInteractions(api);
    }

    /** @return 带分组专用路径的合成路由，额度与实际迁移场景一致。 */
    private LLMModelFileConfigParameter entry() {
        var entry = new LLMModelFileConfigParameter();
        entry.setId("gemini");
        entry.setAdapter(AntigravityModel.PROVIDER);
        entry.setModel("gemini-3.8-flash-medium");
        entry.setApiKey("synthetic-secret");
        entry.setBaseUrl("https://proxy.invalid");
        entry.setApiPath("/antigravity/v1beta/models");
        entry.setMaxTokens(163840);
        entry.setContextWindowTokens(1050000L);
        entry.setImageInput(true);
        // 2026/11/10：原回归明确保留非流式路径，流式传输由独立真实 HTTP 用例覆盖。
        entry.setStream(false);
        return entry;
    }

    /**
     * @param entry 当前可修改的合成路由
     * @return 不访问真实配置的文件端口
     */
    private IFileConfigService files(LLMModelFileConfigParameter entry) {
        var config = new LLMModelFileConfig();
        config.setModels(List.of(entry));
        var files = mock(IFileConfigService.class);
        when(files.get(LLMModelFileConfig.class)).thenReturn(config);
        when(files.requireCurrent(LLMModelFileConfig.class)).thenReturn(config);
        return files;
    }

    /**
     * @param api 可控的原 HTTP 端口
     * @param files 合成文件路由
     * @return 已初始化的真实模型服务
     */
    private LLMModelService service(IAPIResponseService api, IFileConfigService files) {
        var credentials = new FileLLMCredentialResolver(files);
        var adapter = new AntigravityModel(api, credentials, mapper);
        var scanner = mock(IBeanScanner.class);
        when(scanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class)).thenReturn(Map.of(
                "native", IndependentPair.of(AntigravityModel.class.getAnnotation(LLMModel.class), adapter)));
        var service = new LLMModelService(scanner, new LLMFileConfigService(files), () -> Optional.of(LLMModelId.of("gemini")));
        service.init();
        return service;
    }

    /**
     * 真实交互支持原生省略 args 的无参调用，回传签名时保留原始部件，最终次数与实际工具一致。
     * @throws Exception 当有限等待失败时
     */
    @Test
    void nativeToolRoundPassesThroughExistingInteraction() throws Exception {
        var api = mock(IAPIResponseService.class);
        var calls = new AtomicInteger();
        when(api.addTask(any(), eq(AntigravityResponseData.class), eq(true), eq(true), any(ExecutionPolicy.class)))
                .thenAnswer(invocation -> {
                    var request = mapper.valueToTree(invocation.getArgument(0, IAPIRequestData.class));
                    String body;
                    if (calls.getAndIncrement() == 0) {
                        body = """
                                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                                {"functionCall":{"name":"work"},"thoughtSignature":"opaque"}]}}]}
                                """;
                    } else {
                        assertEquals("opaque", request.at("/contents/1/parts/0/thoughtSignature").asString());
                        assertFalse(request.at("/contents/1/parts/0/functionCall").has("args"));
                        assertEquals("work", request.at("/contents/2/parts/0/functionResponse/name").asString());
                        assertEquals("真实合成回执", request.at("/contents/2/parts/0/functionResponse/response/result").asString());
                        assertFalse(request.at("/contents/2/parts/0/functionResponse").has("id"));
                        body = """
                                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"已完成"}]}}]}
                                """;
                    }
                    return new TaskFuture<>(mock(ILogger.class), "受控原生传输",
                            CompletableFuture.completedFuture(mapper.readValue(body, AntigravityResponseData.class)));
                });
        var runtime = new InteractionRuntime();
        var settings = LLMInteractionSettings.defaults();
        var interaction = new LLMInteractionService(service(api, files(entry())), () -> settings,
                runtime, runtime.scheduler);
        var effects = new AtomicInteger();
        var tools = new LLMToolSet(mapper, List.of(new LLMToolBinding(
                LLMTool.builder().name("work").description("无外部副作用的合成工具").build(), (call, context) -> {
                    effects.incrementAndGet();
                    return CompletableFuture.completedStage("真实合成回执");
                })));
        try (var parent = runtime.parent(settings.limits())) {
            var context = new ToolInvocationContext(UUID.randomUUID(),
                    EntitySnapshot.of(EntityId.random(), ComponentPatch.builder().build()), parent);
            var pending = interaction.interact(new LLMPrompt(user(),
                    LLMModelOptions.builder().provider("gemini").build()), tools, context);
            runtime.runAll();
            var result = pending.toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(LLMInteractionResult.Status.SUCCESS, result.status(), () -> String.valueOf(result.failure()));
            assertEquals(2, calls.get());
            assertEquals(1, effects.get());
            assertEquals(1, result.tools().size());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /** 实际注解扫描将新样板加入原目录，查询不连接模型或重新发现扩展。 */
    @Test
    void annotatedTemplateAppearsInExistingDirectory() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var api = mock(IAPIResponseService.class);
            var credentials = mock(ICredentialResolver.class);
            context.registerBean(IAPIResponseService.class, () -> api);
            context.registerBean(ICredentialResolver.class, () -> credentials);
            context.registerBean(ObjectMapper.class, () -> mapper);
            var scan = new ClassPathBeanDefinitionScanner(context, false);
            scan.addIncludeFilter(new AnnotationTypeFilter(LLMModel.class));
            scan.addIncludeFilter(new AnnotationTypeFilter(LLMConfigurationAdapter.class));
            scan.scan(AntigravityModel.class.getPackageName());
            context.refresh();
            var models = mock(ILLMModelService.class);
            when(models.getModels()).thenReturn(Map.of(LLMModelId.of(AntigravityModel.PROVIDER),
                    context.getBean(AntigravityModel.class)));
            var plugins = mock(PluginAccess.class);
            when(plugins.enabled(any(), any())).thenReturn(true);
            var scanner = spy(new BeanScanner(context, mock(ILogger.class)));
            var directory = new LLMConfigurationDirectory(scanner, models, plugins);
            directory.init();
            clearInvocations(scanner);
            var route = directory.describe().routes().getFirst();
            assertEquals("antigravity", route.id());
            assertEquals("", route.defaults().get("api-key"));
            assertEquals("/v1beta/models", route.defaults().get("api-path"));
            assertEquals(route, directory.describe().routes().getFirst());
            verifyNoInteractions(scanner, api, credentials);
        }
    }

    /** 非法能力和凭据在网络请求前拒绝，取消的阶段不进入认证边界。 */
    @Test
    void rejectsUnsupportedInputAndCancelledExecutionBeforeHttp() {
        var api = mock(IAPIResponseService.class);
        var credentials = mock(ICredentialResolver.class);
        var model = new AntigravityModel(api, credentials, mapper);
        assertThrows(IllegalArgumentException.class, () -> model.call(new LLMPrompt(user(),
                LLMModelOptions.builder().logprobs(true).build())));
        assertThrows(LLMConfigurationException.class, () -> model.call(new LLMPrompt(user(),
                LLMModelOptions.builder().metadata(Map.of("apiKey", "private-test")).build())));
        try (var control = ExecutionControl.create()) {
            control.cancel();
            assertThrows(ExecutionStoppedException.class, () -> model.call(new LLMPrompt(user()),
                    ExecutionPolicy.controlled(null, control)));
        }
        verifyNoInteractions(api, credentials);
    }
}
