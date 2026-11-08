package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatModel;
import org.zexnocs.teanekoagent.llm.instance.kimi.KimiChatModel;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionsModel;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesModel;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 用受控 HTTP 服务验证各供应商认证与连接参数的可信边界。
 * <br>2026/11/03：凭据替身同步采用带参数代次的请求边界。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：验证同协议不同路由的密钥隔离及跨协议拒绝。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：适配模型专属 configuration 包，保留原请求边界验证。
 *
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
class LLMProviderRequestBoundaryTest {
    /** 2026/11/04：同协议两条路由选择不同型号与密钥，且不能将其他协议路由交给当前适配器。 */
    @Test
    void aliasesKeepTheirOwnConfigurationAndCredentials() {
        var files = mock(org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService.class);
        var config = new org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig();
        var entries = new java.util.ArrayList<org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter>();
        for (String suffix : List.of("a", "b")) {
            var entry = new org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter();
            entry.setId("deepseek-" + suffix);
            entry.setAdapter("deepseek");
            entry.setModel("deepseek-flash");
            entry.setApiKey("仅测试密钥-" + suffix);
            entry.setMaxTokens(2048);
            entry.setContextWindowTokens(1048576L);
            entries.add(entry);
        }
        config.setModels(entries);
        when(files.get(org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig.class)).thenReturn(config);
        when(files.requireCurrent(org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig.class)).thenReturn(config);
        var api = mock(IAPIResponseService.class);
        doAnswer(ignored -> new TaskFuture<>(mock(ILogger.class), "离线模型", new CompletableFuture<>()))
                .when(api).addTask(any(), any(), eq(true), eq(true));
        // 2026/11/05：使用归入模型 configuration 包的凭据解析器。
        var credentials = new org.zexnocs.teanekoagent.llm.configuration.FileLLMCredentialResolver(files);
        var model = new DeepSeekChatModel(api, credentials);
        var scanner = mock(org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner.class);
        var annotation = DeepSeekChatModel.class.getAnnotation(org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel.class);
        when(scanner.getBeansWithAnnotationAndInterface(org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel.class,
                org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel.class))
                .thenReturn(Map.of("deepseek", org.zexnocs.teanekocore.framework.pair.IndependentPair.of(annotation, model)));
        var service = new LLMModelService(scanner, new org.zexnocs.teanekoagent.llm.file_config.LLMFileConfigService(files),
                () -> java.util.Optional.of(LLMModelId.of("deepseek-a")));
        service.init();
        service.call(prompt(LLMModelOptions.builder().provider("deepseek-a").build()));
        service.call(prompt(LLMModelOptions.builder().provider("deepseek-b").build()));
        var capture = org.mockito.ArgumentCaptor.forClass(IAPIRequestData.class);
        verify(api, times(2)).addTask(capture.capture(), any(), eq(true), eq(true));
        assertEquals(List.of("Bearer 仅测试密钥-a", "Bearer 仅测试密钥-b"),
                capture.getAllValues().stream().map(request -> request.headers().get("Authorization")).toList());
        entries.getLast().setAdapter("kimi");
        assertThrows(IllegalArgumentException.class, () -> model.call(prompt(
                LLMModelOptions.builder().provider("deepseek-b").model("deepseek-flash").build())));
        assertThrows(LLMConfigurationException.class, () -> credentials.require("deepseek",
                LLMModelOptions.builder().provider("deepseek-b").build()));
        verify(api, times(2)).addTask(any(), any(), eq(true), eq(true));
    }
    private static final String SECRET = "test-only-provider-secret";
    private static final List<BiFunction<IAPIResponseService, ICredentialResolver, AbstractLLMModel>> MODELS = List.of(
            DeepSeekChatModel::new, OpenAIResponsesModel::new, KimiChatModel::new, OpenAIChatCompletionsModel::new);

    /**
     * 验证所有适配器仅在 HTTP 边界解析凭据，不修改 Prompt 或普通选项。
     *
     */
    @Test
    void allAdaptersResolveCredentialsAtHttpBoundaryWithoutChangingPromptOrOptions() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var credentials = mock(ICredentialResolver.class);
            var model = factory.apply(api, credentials);
            // 2026/11/03：验证实际调用的新凭据入口，继续保持原有请求隔离断言。
            when(credentials.require(eq(model.getProvider()), any())).thenReturn(new LLMProviderAccess(SECRET,
                    "https://trusted-gateway.test/v1", "/trusted-call"));
            var pending = new CompletableFuture<>();
            doAnswer(_ -> new TaskFuture<>(mock(ILogger.class), "fake-http", pending))
                    .when(api).addTask(any(), any(), eq(true), eq(true));
            var options = LLMModelOptions.builder().provider(model.getProvider()).model("configured-test-model")
                    .metadata(Map.of("baseUrl", "https://untrusted.test/v1", "api", "//untrusted.test/call"))
                    .build();
            var prompt = prompt(options);
            var metadataBefore = Map.copyOf(options.getMetadata());
            var jsonBefore = new ObjectMapper().writeValueAsString(prompt.getMessages());
            var result = model.call(prompt);
            assertFalse(result.asStage().toCompletableFuture().isDone());
            var capture = org.mockito.ArgumentCaptor.forClass(IAPIRequestData.class);
            verify(api).addTask(capture.capture(), any(), eq(true), eq(true));
            verify(credentials).require(eq(model.getProvider()), any());
            var request = capture.getValue();
            assertEquals("https://trusted-gateway.test/v1", request.getBaseUrlOverride());
            assertEquals("/trusted-call", request.getPathOverride());
            assertEquals("Bearer " + SECRET, request.headers().get("Authorization"));
            assertEquals(0, request.getClass().getAnnotation(APIRequestData.class).retryCount());
            var json = new ObjectMapper().writeValueAsString(request);
            assertFalse(json.contains(SECRET));
            assertFalse(json.contains("untrusted.test"));
            assertTrue(json.contains("configured-test-model"));
            assertEquals(jsonBefore, new ObjectMapper().writeValueAsString(prompt.getMessages()));
            assertEquals(metadataBefore, options.getMetadata());
            assertEquals("configured-test-model", options.getModel());
            assertFalse(jsonBefore.contains(SECRET));
            verifyNoMoreInteractions(api, credentials);
        }
    }

    /**
     * 验证本地连接配置缺失时采用供应商默认地址，Prompt 不能覆盖目标。
     *
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     */
    @Test
    void absentSpringEndpointUsesVendorDefaultAndIgnoresPromptEndpoint() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var model = factory.apply(api, ignored -> new LLMProviderAccess(SECRET, null, null));
            doAnswer(invocation -> new TaskFuture<>(mock(ILogger.class), "fake-http", new CompletableFuture<>()))
                    .when(api).addTask(any(), any(), eq(true), eq(true));
            model.call(prompt(LLMModelOptions.builder().metadata(Map.of("baseUrl", "https://untrusted.test", "api", "/untrusted")).build()));
            var capture = org.mockito.ArgumentCaptor.forClass(IAPIRequestData.class);
            verify(api).addTask(capture.capture(), any(), eq(true), eq(true));
            var request = capture.getValue();
            assertEquals(switch (model.getProvider()) {
                case "deepseek" -> "https://api.deepseek.com";
                case "kimi" -> "https://api.moonshot.cn/v1";
                default -> "https://api.openai.com/v1";
            }, request.getBaseUrlOverride());
            assertEquals(model.getProvider().equals("openai") ? "/responses" : "/chat/completions", request.getPathOverride());
        }
    }

    /**
     * 验证凭据缺失时不提交任何 HTTP 请求。
     */
    @Test
    void missingCredentialNeverSubmitsHttpRequest() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var credentials = mock(ICredentialResolver.class);
            var model = factory.apply(api, credentials);
            // 2026/11/03：缺失凭据也经带参数版本的入口失败，HTTP 仍不得启动。
            when(credentials.require(eq(model.getProvider()), any())).thenThrow(new LLMConfigurationException("测试凭据缺失。"));
            assertThrows(LLMConfigurationException.class, () -> model.call(prompt(null)));
            verifyNoInteractions(api);
        }
    }

    /**
     * 验证选项夹带认证信息在凭据读取和 HTTP 启动前被拒绝。
     */
    @Test
    void secretInOptionsIsRejectedBeforeCredentialResolutionOrHttp() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var credentials = mock(ICredentialResolver.class);
            var model = factory.apply(api, credentials);
            var options = LLMModelOptions.builder().metadata(Map.of("apiKey", SECRET)).build();
            var error = assertThrows(LLMConfigurationException.class, () -> model.call(prompt(options)));
            assertFalse(error.toString().contains(SECRET));
            verifyNoInteractions(api, credentials);
        }
    }

    /**
     * 验证直接适配器调用也在 HTTP 前拒绝不支持的参数。
     */
    @Test
    void directModelCallAlsoChecksUnsupportedOptionsBeforeHttp() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var credentials = mock(ICredentialResolver.class);
            var model = factory.apply(api, credentials);
            assertThrows(IllegalArgumentException.class, () -> model.call(prompt(LLMModelOptions.builder().stream(true).build())));
            verifyNoInteractions(api, credentials);
        }
    }

    /**
     * 以当前用例选项构造最小消息输入，便于核对调用是否改写原数据。
     *
     * @param options 本次有效模型选项
     * @return 满足本方法约束的 {@code LLMPrompt} 结果
     */
    private static LLMPrompt prompt(LLMModelOptions options) {
        return new LLMPrompt(LLMUserMessage.builder().contents(LLMContentListBuilder.builder().addText("hello").build()).build(), options);
    }
}
