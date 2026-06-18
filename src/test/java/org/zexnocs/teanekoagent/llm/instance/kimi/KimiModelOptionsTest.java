package org.zexnocs.teanekoagent.llm.instance.kimi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;

import java.util.List;
import java.util.Map;

/**
 * Kimi 模型参数和能力声明测试。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class KimiModelOptionsTest {
    /**
     * 验证 file config metadata 能够转换为 Kimi 专用参数。
     */
    @Test
    void copiesKimiOptionsFromMetadata() {
        var source = LLMModelOptions.builder()
                .provider("kimi")
                .model("kimi-k2.6")
                .metadata(Map.of(
                        "api", "/chat/completions-test",
                        "kimi.thinkingKeep", true,
                        "kimi.promptCacheKey", "cache-key",
                        "kimi.safetyIdentifier", "user-hash"
                ))
                .build();

        var options = KimiModelOptions.copyOf(source);

        Assertions.assertEquals("/chat/completions-test", options.findApiPath().orElseThrow());
        Assertions.assertTrue(options.findThinkingKeep().orElseThrow());
        Assertions.assertEquals("cache-key", options.findPromptCacheKey().orElseThrow());
        Assertions.assertEquals("user-hash", options.findSafetyIdentifier().orElseThrow());
    }

    /**
     * 验证 K2 系列拒绝不支持的采样参数和流式调用，并限制 K2.7 Code 关闭思考。
     */
    @Test
    void validatesKimiModelSpecificOptions() {
        var model = new KimiChatModel(Mockito.mock(IAPIResponseService.class));

        Assertions.assertTrue(model.supports(KimiModelOptions.baseOptions()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("kimi")
                .model("kimi-k2.6")
                .temperature(0.7)
                .build()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("kimi")
                .model("kimi-k2.6")
                .stopWords(List.of("stop"))
                .build()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("kimi")
                .model(KimiModelOptions.KIMI_K2_7_CODE)
                .thinking(false)
                .build()));
        Assertions.assertFalse(model.supports(LLMModelOptions.builder()
                .provider("kimi")
                .stream(true)
                .build()));
    }
}
