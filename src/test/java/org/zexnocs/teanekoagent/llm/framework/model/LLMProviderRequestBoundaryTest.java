package org.zexnocs.teanekoagent.llm.framework.model;

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
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMProviderRequestBoundaryTest {
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
            when(credentials.require(model.getProvider())).thenReturn(new LLMProviderAccess(SECRET,
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
            verify(credentials).require(model.getProvider());
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
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void absentSpringEndpointUsesVendorDefaultAndIgnoresPromptEndpoint() throws Exception {
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
            when(credentials.require(model.getProvider())).thenThrow(new LLMConfigurationException("Missing Spring credential."));
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
