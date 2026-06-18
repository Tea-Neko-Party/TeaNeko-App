package org.zexnocs.teanekoagent_old.llm.instance.openai.completions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 通用适配层测试。
 * <br>验证配置复制、可选字段过滤、参与者名称和模型能力声明。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class OpenAIChatCompletionsModelTest {
    /**
     * 验证通用 metadata 和消息能够构造 Chat Completions 请求。
     */
    @Test
    void mapsGenericOptionsAndOmitsBlankExtensions() {
        var source = LLMModelOptions.builder()
                .provider(OpenAIChatCompletionsModel.PROVIDER)
                .model("compatible-model")
                .maxTokens(512)
                .logprobs(true)
                .metadata(Map.of(
                        "baseUrl", "https://example.com/v1",
                        "api", "/chat/completions-test",
                        "openaiChat.topLogprobs", 3,
                        "body.vendor_flag", true,
                        "body.empty_value", "",
                        "header.X-Vendor", "test"
                ))
                .build();
        var options = OpenAIChatCompletionModelOptions.copyOf(source);
        var prompt = new LLMPrompt(List.of(LLMUserMessage.builder()
                .name("alice")
                .contents(LLMContentListBuilder.builder().addText("hello").build())
                .build()));

        var request = OpenAIChatCompletionMapper.toRequest(prompt, options, "secret-key");
        var json = new ObjectMapper().writeValueAsString(request);

        Assertions.assertEquals("https://example.com/v1", request.getBaseUrl());
        Assertions.assertEquals("/chat/completions-test", request.getApiPath());
        Assertions.assertEquals("alice", request.getMessages().getFirst().get("name"));
        Assertions.assertEquals(512, request.getMaxCompletionTokens());
        Assertions.assertEquals(3, request.getTopLogprobs());
        Assertions.assertEquals(true, request.getExtraBody().get("vendor_flag"));
        Assertions.assertFalse(request.getExtraBody().containsKey("empty_value"));
        Assertions.assertEquals("test", request.headers().get("X-Vendor"));
        Assertions.assertFalse(json.contains("secret-key"));
    }

    /**
     * 验证独立注册的 OpenAI Chat Completions 模型拒绝其他 provider 和流式调用。
     */
    @Test
    void validatesProviderAndNonStreamingContract() {
        var model = new OpenAIChatCompletionsModel(Mockito.mock(IAPIResponseService.class));

        Assertions.assertTrue(model.supports(OpenAIChatCompletionModelOptions.baseOptions()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("openai")
                .build()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider(OpenAIChatCompletionsModel.PROVIDER)
                .stream(true)
                .build()));
    }

    /**
     * 验证 OpenAI 模型将统一 thinking 开关转换为 reasoning_effort。
     */
    @Test
    void mapsThinkingToOpenAIReasoningEffort() {
        var model = new TestOpenAIChatCompletionsModel(Mockito.mock(IAPIResponseService.class));
        var options = LLMModelOptions.builder()
                .provider(OpenAIChatCompletionsModel.PROVIDER)
                .thinking(true)
                .build();

        var prepared = model.prepare(options);

        Assertions.assertEquals("medium", prepared.getMetadata().get("body.reasoning_effort"));
        Assertions.assertTrue(model.supports(options));
    }

    /**
     * 暴露受保护参数转换方法的测试模型。
     *
     * @author zExNocs
     * @date 2026/06/10
     * @since 4.4.1
     */
    private static final class TestOpenAIChatCompletionsModel extends OpenAIChatCompletionsModel {
        /**
         * 创建测试模型。
         *
         * @param apiResponseService API 响应服务
         */
        private TestOpenAIChatCompletionsModel(IAPIResponseService apiResponseService) {
            super(apiResponseService);
        }

        /**
         * 调用受保护的参数转换逻辑。
         *
         * @param options 统一参数
         * @return Chat Completions 参数
         */
        private OpenAIChatCompletionModelOptions prepare(LLMModelOptions options) {
            return prepareOptions(options);
        }
    }
}
