package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证本地估算包含正文、图像和工具定义的成本，且不会执行工具或暴露其运行对象。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMTokenEstimatorTest {
    private final JsonLLMTokenEstimator estimator = new JsonLLMTokenEstimator(new ObjectMapper());

    /** 验证透明状态和多模态输入都影响估算，保留 JSON null 不会造成解析失败。 */
    @Test void estimatesMessagesImagesAndOpaqueState() {
        var message = LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("文本").build()).build();
        var plain = estimator.estimate(new LLMPrompt(List.of(message))).inputTokens();
        var expanded = LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("文本")
                .addImage("https://example.com/image.png").build())
                .providerMetadata(Map.of("opaque", Arrays.asList("透明状态", null))).build();
        var estimate = estimator.estimate(new LLMPrompt(LLMProtocolSnapshots.messages(List.of(expanded))));
        assertTrue(estimate.inputTokens() > plain);
        assertEquals("utf8-json-v1", estimate.method());
    }

    /** 验证工具 schema 计入准入成本，但估算不能调用其执行器。 */
    @Test void estimatesToolDefinitionsWithoutInvokingExecutor() {
        var tools = new LLMToolSet(new ObjectMapper(), List.of(new LLMToolBinding(
                LLMTool.builder().name("work").description("工具说明").build(), (call, context) -> {
                    fail("估算不能执行工具。");
                    return CompletableFuture.completedStage("");
                })));
        var plain = new LLMPrompt(List.of());
        var expanded = new LLMPrompt(List.of(), LLMModelOptions.builder().tools(tools.definitions()).build());
        assertTrue(estimator.estimate(expanded).inputTokens() > estimator.estimate(plain).inputTokens());
    }

    /** 验证非法估算和自由文本算法标识会被拒绝，不允许将输入或密钥混入结果标签。 */
    @Test void rejectsMalformedEstimatesAndUsage() {
        assertThrows(IllegalArgumentException.class, () -> new LLMTokenEstimate(-1, "fixed-v1"));
        assertThrows(IllegalArgumentException.class, () -> new LLMTokenEstimate(1, "不应导出的自由文本"));
        assertThrows(IllegalArgumentException.class, () -> new LLMTokenUsage(-1, LLMTokenUsage.Source.REPORTED, 0, "", false));
    }
}
