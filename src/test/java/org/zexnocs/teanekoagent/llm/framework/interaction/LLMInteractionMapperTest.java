package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionResponseData;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionResponseData;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesMapper;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesResponseData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证真实供应商映射与交互快照兼容，拒绝和未完整响应不进入工具副作用路径。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMInteractionMapperTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** 验证三类协议映射均保留 usage 缺失，默认 DTO 也不制造零用量。 */
    @Test void allMappersPreserveAbsentUsage() {
        assertNull(LLMResult.builder().build().getUsage());
        assertNull(DeepSeekChatCompletionMapper.toResult(new DeepSeekChatCompletionResponseData()).getUsage());
        assertNull(OpenAIChatCompletionMapper.toResult(new OpenAIChatCompletionResponseData()).getUsage());
        assertNull(OpenAIResponsesMapper.toResult(new OpenAIResponsesResponseData()).getUsage());
    }

    /** 验证 Chat Completions 的 refusal 字段映射到统一拒绝状态且说明可呈现。 */
    @Test void chatRefusalRemainsAnExplicitInteractionTermination() {
        var data = mapper.readValue("""
                {"choices":[{"index":0,"finish_reason":"stop","message":
                {"role":"assistant","content":null,"refusal":"拒绝说明"}}]}
                """, OpenAIChatCompletionResponseData.class);
        var result = interact(OpenAIChatCompletionMapper.toResult(data));
        assertEquals(LLMInteractionResult.Status.REFUSED, result.status());
        assertTrue(result.committableTranscript().isEmpty());
        assertTrue(mapper.writeValueAsString(result.finalCandidate().orElseThrow()).contains("拒绝说明"));
    }

    /** 验证 Responses 拒绝和未完整的工具输出不会误判为可执行工具请求。 */
    @Test void responsesRefusalAndIncompleteCallsCannotExecuteTools() {
        var refused = mapper.readValue("""
                {"status":"completed","output":[{"type":"message","content":
                [{"type":"refusal","refusal":"拒绝说明"}]}]}
                """, OpenAIResponsesResponseData.class);
        assertEquals(LLMInteractionResult.Status.REFUSED, interact(OpenAIResponsesMapper.toResult(refused)).status());
        var incomplete = mapper.readValue("""
                {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},
                "output":[{"type":"function_call","call_id":"a","name":"work","arguments":"{}"}]}
                """, OpenAIResponsesResponseData.class);
        var result = interact(OpenAIResponsesMapper.toResult(incomplete));
        assertEquals(LLMInteractionResult.Status.TRUNCATED, result.status());
        assertEquals(1, result.modelAttempts());
        assertTrue(result.tools().isEmpty());
    }

    /** 验证冻结后的工具、图像及透明推理状态可被现有 mapper 正确回放和序列化。 */
    @Test void frozenProtocolRemainsCompatibleWithRealMappersAndExport() {
        var raw = mapper.readValue("""
                {"status":"completed","output":[
                {"type":"reasoning","id":"r1","encrypted_content":"透明加密状态",
                "summary":[{"type":"summary_text","text":"透明摘要"}]},
                {"type":"function_call","call_id":"a","name":"work","arguments":"{}"}]}
                """, OpenAIResponsesResponseData.class);
        var messages = new ArrayList<ILLMMessage>();
        messages.add(LLMUserMessage.builder().contents(new LLMContentListBuilder()
                .addText("包含图片").addImage("https://example.com/image.png").build()).build());
        messages.add(OpenAIResponsesMapper.toResult(raw).getFirstMessage().orElseThrow());
        messages.add(LLMToolMessage.builder().name("work").toolCallId("a")
                .contents(new LLMContentListBuilder().addText("工具结果").build()).build());
        var snapshot = LLMProtocolSnapshots.messages(messages);
        var prompt = new LLMPrompt(snapshot);
        var responses = OpenAIResponsesMapper.toRequest(prompt, OpenAIModelOptions.baseOptions(), "离线测试密钥");
        var chat = OpenAIChatCompletionMapper.toRequest(prompt, OpenAIChatCompletionModelOptions.baseOptions(), "离线测试密钥");
        var deepseek = DeepSeekChatCompletionMapper.toRequest(prompt, DeepSeekModelOptions.baseOptions(),
                "https://example.com", "/chat/completions", "离线测试密钥");
        var json = mapper.writeValueAsString(responses);
        assertTrue(json.contains("透明加密状态"));
        assertTrue(json.contains("function_call_output"));
        assertTrue(json.contains("image.png"));
        assertTrue(mapper.writeValueAsString(chat).contains("tool_call_id"));
        assertTrue(mapper.writeValueAsString(deepseek).contains("tool_call_id"));
        assertFalse(mapper.writeValueAsString(snapshot).contains("离线测试密钥"));
    }

    /**
     * 用真实编排与固定响应端口验证供应商结果，不启动 HTTP、数据库或 Spring。
     * @param response 已经过真实 mapper 的统一响应
     * @return 本次完整交互结果
     */
    private LLMInteractionResult interact(ILLMResult response) {
        var runtime = new InteractionRuntime();
        var models = mock(ILLMModelService.class);
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(ignored ->
                new TaskFuture<>(mock(ILogger.class), "协议离线模型", CompletableFuture.completedFuture(response)));
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var tools = new LLMToolSet(mapper, List.of(new LLMToolBinding(
                    LLMTool.builder().name("work").build(), (call, context) -> {
                        fail("拒绝或截断响应不允许执行工具。");
                        return null;
                    })));
            var context = new ToolInvocationContext(UUID.randomUUID(),
                    EntitySnapshot.of(EntityId.random(), ComponentPatch.builder().build()), parent);
            var service = new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler);
            var pending = service.interact(new LLMPrompt(LLMUserMessage.builder().contents(List.of()).build()), tools, context);
            runtime.runAll();
            return pending.toCompletableFuture().join();
        }
    }
}
