package org.zexnocs.teanekoagent.llm.instance;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionRequestData;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionRequestData;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesMapper;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesRequestData;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证供应商 body 扩展不会覆盖模型、输入、工具或其他受控字段。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMRequestExtensionBoundaryTest {
    /**
     * 验证所有 mapper 拒绝通过扩展覆盖原生输入、路由、工具、流式及服务端上下文。
     */
    @Test
    void allMappersRejectNativeInputRoutingToolsStreamingAndContextOverrides() {
        for (var field : List.of("model", "messages", "input", "tools", "tool_choice", "stream", "instructions",
                "previous_response_id", "conversation", "prompt", "background", "max_tokens", "max_output_tokens", "max_completion_tokens")) {
            var options = LLMModelOptions.builder().metadata(Map.of("body." + field, "untrusted-override")).build();
            var prompt = new LLMPrompt(List.of());
            assertThrows(IllegalArgumentException.class, () -> DeepSeekChatCompletionMapper.toRequest(prompt, options, "https://example.test", "/chat", "fake-key"));
            assertThrows(IllegalArgumentException.class, () -> OpenAIResponsesMapper.toRequest(prompt, options, "fake-key"));
            assertThrows(IllegalArgumentException.class, () -> OpenAIChatCompletionMapper.toRequest(prompt, OpenAIChatCompletionModelOptions.copyOf(options), "fake-key"));
        }
    }

    /**
     * 验证即使类型字段未设置或扩展值为空，冲突字段也会被拒绝。
     */
    @Test
    void collisionsAreRejectedEvenWhenTheTypedFieldIsAbsentOrExtensionIsEmpty() {
        for (var type : List.of(DeepSeekChatCompletionRequestData.class, OpenAIChatCompletionRequestData.class, OpenAIResponsesRequestData.class)) {
            for (var field : List.of("temperature", "top_p", "topP", "apiKey", "baseUrl")) {
                assertThrows(IllegalStateException.class, () -> LLMRequestBodyExtensions.fromMetadata(Map.of("body.apiKey", "secret"), type));
                if (!field.equals("apiKey")) {
                    assertThrows(IllegalArgumentException.class, () -> LLMRequestBodyExtensions.fromMetadata(Map.of("body." + field, ""), type));
                }
            }
        }
    }

    /**
     * 验证真实供应商扩展保留 false 和零，同时省略空值。
     */
    @Test
    void genuineVendorExtensionsKeepFalseAndZeroButOmitEmptyValues() {
        var metadata = Map.of("body.vendor_flag", false, "body.vendor_count", 0,
                "body.empty_text", "", "body.empty_list", List.of(), "vendor.reasoning", "medium");
        for (var type : List.of(DeepSeekChatCompletionRequestData.class, OpenAIChatCompletionRequestData.class, OpenAIResponsesRequestData.class)) {
            var extra = LLMRequestBodyExtensions.fromMetadata(metadata, type);
            assertEquals(Map.of("vendor_flag", false, "vendor_count", 0), extra);
            assertThrows(UnsupportedOperationException.class, extra::clear);
        }
    }
}
