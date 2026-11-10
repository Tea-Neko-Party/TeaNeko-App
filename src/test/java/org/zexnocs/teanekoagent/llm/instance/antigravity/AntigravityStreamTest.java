package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原生响应聚合验收，覆盖终态、签名、累计用量、拒答和容量边界。
 * <br>2026/11/10：多事件不可提前形成工具调用，正常 EOF 也不能替代协议终态。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
class AntigravityStreamTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param target 本次聚合
     * @param event 合成事件
     */
    private void accept(AntigravityStreamAccumulator target, String event) {
        target.accept(mapper.readValue(event, AntigravityResponseData.class));
    }

    /** 保留原生顺序和签名，最终用量快照不能与前片重复相加。 */
    @Test
    void aggregatesPartsSignaturesAndLatestUsage() {
        var target = new AntigravityStreamAccumulator(mapper);
        accept(target, """
                {"responseId":"same","candidates":[{"index":0,"content":{"parts":[
                 {"text":"内部","thought":true},{"text":"你"}]}}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":1,"totalTokenCount":11}}
                """);
        accept(target, """
                {"responseId":"same","candidates":[{"index":0,"content":{"parts":[
                 {"text":"好"},{"functionCall":{"name":"work","args":{}},"thoughtSignature":"opaque"}]},
                 "finishReason":"STOP"}]}
                """);
        accept(target, """
                {"modelVersion":"synthetic-model",
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":3,"thoughtsTokenCount":2,"totalTokenCount":15}}
                """);
        var response = target.finish();
        var result = AntigravityResponseMapper.toResult(response, mapper);
        var choice = result.getChoices().getFirst();
        assertEquals("tool_calls", choice.getFinishReason());
        assertEquals(1, choice.getMessage().getContents().size());
        assertEquals("你好", choice.getMessage().getContents().stream()
                .map(item -> ((TextLLMContentPart) item.getContentPart()).getText()).reduce("", String::concat));
        assertEquals(1, choice.getMessage().getToolCalls().size());
        assertEquals("opaque", response.candidates().getFirst().at("/content/parts/3/thoughtSignature").asString());
        assertEquals(15, result.getUsage().getTotalTokens());
        assertEquals(5, result.getUsage().getCompletionTokens());
        assertEquals(10, result.getUsage().getPromptTokens());
    }

    /** 缺少候选或某个候选缺少终态，即使 EOF 正常也不能成功。 */
    @Test
    void rejectsEmptyAndPartialStreams() {
        for (String event : new String[]{"{}", """
                {"candidates":[{"content":{"parts":[{"functionCall":{"name":"work"}}]}}]}
                """, """
                {"candidates":[{"index":0,"finishReason":"STOP","content":{"parts":[{"text":"OK"}]}},
                  {"index":1,"content":{"parts":[{"text":"未完"}]}}]}
                """}) {
            var target = new AntigravityStreamAccumulator(mapper);
            accept(target, event);
            var failure = assertThrows(AntigravityStreamException.class, target::finish);
            assertTrue(failure.failureReason().contains("finishReason"));
        }
    }

    /** 终态后的新正文和响应身份变化明确拒绝，不能把两份响应拼接。 */
    @Test
    void rejectsChangedIdentityAndContentAfterFinish() {
        for (String next : new String[]{"{\"responseId\":\"other\"}", """
                {"candidates":[{"content":{"parts":[{"text":"迟到"}]}}]}
                """, """
                {"candidates":[{"finishReason":"STOP"}]}
                """}) {
            var target = new AntigravityStreamAccumulator(mapper);
            accept(target, """
                    {"responseId":"first","candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"OK"}]}}]}
                    """);
            assertThrows(AntigravityStreamException.class, () -> accept(target, next));
        }
    }

    /** 截断时的部分工具不可执行，输入阻止按拒答终态保留。 */
    @Test
    void truncatedToolsAndBlockedPromptsCannotBecomeCalls() {
        var truncated = new AntigravityStreamAccumulator(mapper);
        accept(truncated, """
                {"candidates":[{"content":{"parts":[{"functionCall":{"name":"work"}}]},"finishReason":"MAX_TOKENS"}]}
                """);
        var choice = AntigravityResponseMapper.toResult(truncated.finish(), mapper).getChoices().getFirst();
        assertEquals("length", choice.getFinishReason());
        assertTrue(choice.getMessage().getToolCalls().isEmpty());
        var blocked = new AntigravityStreamAccumulator(mapper);
        accept(blocked, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertEquals("content_filter",
                AntigravityResponseMapper.toResult(blocked.finish(), mapper).getChoices().getFirst().getFinishReason());
    }

    /** 无终态的持续小事件也不能无界累积，容量诊断不包含正文。 */
    @Test
    void boundsSmallEventsWithoutLeakingPayload() {
        var target = new AntigravityStreamAccumulator(mapper);
        var event = mapper.readValue("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"private-payload\"}]}}]}",
                AntigravityResponseData.class);
        for (int index = 0; index < 8192; index++) {
            target.accept(event);
        }
        var failure = assertThrows(AntigravityStreamException.class, () -> target.accept(event));
        assertFalse(failure.failureReason().contains("private-payload"));
        assertTrue(failure.failureReason().contains("8192"));
    }
}
