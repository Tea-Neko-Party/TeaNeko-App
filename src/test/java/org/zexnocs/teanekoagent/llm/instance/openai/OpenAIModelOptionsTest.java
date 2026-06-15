package org.zexnocs.teanekoagent.llm.instance.openai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesModel;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 模型参数与能力声明测试。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class OpenAIModelOptionsTest {
    /**
     * 验证通用 file config metadata 能够转换为 OpenAI 专用参数。
     */
    @Test
    void copiesProviderOptionsFromMetadata() {
        var source = LLMModelOptions.builder()
                .provider("openai")
                .model("gpt-5.5")
                .metadata(Map.of(
                        "baseUrl", "https://example.test/v1",
                        "api", "/responses-test",
                        "openai.organization", "org-test",
                        "openai.reasoningEffort", "high",
                        "openai.topLogprobs", 4,
                        "openai.metadata", Map.of("trace", "test")
                ))
                .build();

        var options = OpenAIModelOptions.copyOf(source);

        Assertions.assertEquals("https://example.test/v1", options.findBaseUrl().orElseThrow());
        Assertions.assertEquals("/responses-test", options.findApiPath().orElseThrow());
        Assertions.assertEquals("org-test", options.findOrganization().orElseThrow());
        Assertions.assertEquals("high", options.findReasoningEffort().orElseThrow());
        Assertions.assertEquals(4, options.findTopLogprobs().orElseThrow());
        Assertions.assertEquals("test", options.getRequestMetadata().get("trace"));
    }

    /**
     * 验证 OpenAI 适配器拒绝当前 Responses 映射无法表达的通用参数。
     */
    @Test
    void rejectsUnsupportedResponsesOptions() {
        var model = new OpenAIResponsesModel(Mockito.mock(IAPIResponseService.class));

        Assertions.assertTrue(model.supports(OpenAIModelOptions.baseOptions()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("openai")
                .stream(true)
                .build()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("openai")
                .stopWords(List.of("stop"))
                .build()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("openai")
                .frequencyPenalty(0.5)
                .build()));
    }
}
