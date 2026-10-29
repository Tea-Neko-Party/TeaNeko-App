package org.zexnocs.teanekoagent.agent.conversation;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证完整轮次窗口、规范化输入预算和透明协议边界，不启动线程或外部服务。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentConversationEngineTest {
    private final AgentConversationEngine engine = new AgentConversationEngine(new ObjectMapper());
    private final LLMMessageCodec codec = new LLMMessageCodec(new ObjectMapper());
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final AgentConversationStamp stamp = new AgentConversationStamp(new AgentConversationKey(EntityId.random(), scope), UUID.randomUUID());

    /**
     * 创建保留真实 UUID 的新增输入。
     * @param text 新消息正文
     * @return 原始请求
     */
    private AgentRunRequest request(String text) {
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "平台用户", "来源", "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()));
    }

    /**
     * 创建没有工具调用的正常终止候选。
     * @param text 回答正文
     * @return 统一候选
     */
    private ILLMAssistantMessage answer(String text) {
        return LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build();
    }

    /**
     * 使用明确完整 transcript 创建可提交结果。
     * @param prepared 本轮输入
     * @param transcript 完整协议链
     * @param candidate 最终候选
     * @return 带有效准入的成功结果
     */
    private AgentRunResult success(AgentConversationEngine.Prepared prepared, List<ILLMMessage> transcript, ILLMAssistantMessage candidate) {
        var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").build()),
                ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
        var access = new AgentAccessDecision(AgentAccessDecision.Reason.ALLOWED,
                Optional.of(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE)),
                Optional.of(new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime))));
        var interaction = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, transcript, candidate, List.of(), List.of(), Map.of());
        return new AgentRunResult(stamp.key().agentId(), prepared.request().requestId(), AgentRunResult.Status.SUCCESS,
                Optional.of(access), Optional.of(interaction), Map.of());
    }

    /**
     * 创建普通完整轮次。
     * @param prepared 本轮输入
     * @param text 回答正文
     * @return 完整成功结果
     */
    private AgentRunResult success(AgentConversationEngine.Prepared prepared, String text) {
        var candidate = answer(text); var messages = new ArrayList<>(prepared.request().messages()); messages.add(candidate);
        return success(prepared, messages, candidate);
    }

    /** 验证多个用户共享窗口时保留各自稳定身份，原始输入没有被修改。 */
    @Test void sharedWindowPreservesEachActorAndOnlyAppendsNewDelta() {
        var first = request("第一位用户");
        var originalName = first.messages().getFirst().getName();
        var prepared = engine.prepare(first, stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        assertEquals(originalName, first.messages().getFirst().getName());
        assertTrue(prepared.request().messages().getFirst().getName().startsWith("user_"));
        var window = Objects.requireNonNull(engine.complete(prepared, success(prepared, "第一次回答")));
        var second = request("第二位用户");
        var next = engine.prepare(second, stamp, window, AgentConversationOptions.defaults());
        assertEquals(3, next.request().messages().size());
        var updated = Objects.requireNonNull(engine.complete(next, success(next, "第二次回答")));
        assertEquals(4, updated.messages().size()); assertEquals(2, updated.turns().size());
        assertEquals(first.actor(), updated.turns().getFirst().actor()); assertEquals(second.actor(), updated.turns().getLast().actor());
        assertNotEquals(updated.messages().getFirst().getName(), updated.messages().get(2).getName());
    }

    /** 验证完整工具往返和透明状态保留，窗口限额不会截取单轮中间部分。 */
    @Test void windowKeepsWholeToolRoundAndDiscardsOversizedTurnExplicitly() {
        var prepared = engine.prepare(request("使用工具"), stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var call = LLMToolCall.builder().id("调用一").name("查询").arguments("{}").build();
        var toolRequest = LLMAssistantMessage.builder().toolCalls(List.of(call)).contents(List.of())
                .providerMetadata(Map.of("reasoning_content", "透明状态")).build();
        var toolResult = LLMToolMessage.builder().name("查询").toolCallId("调用一")
                .contents(new LLMContentListBuilder().addText("已完成").build()).build();
        var finalMessage = answer("完成回答"); var messages = new ArrayList<>(prepared.request().messages());
        messages.add(toolRequest); messages.add(toolResult); messages.add(finalMessage);
        var result = success(prepared, messages, finalMessage);
        var window = Objects.requireNonNull(engine.complete(prepared, result));
        assertEquals(4, window.messages().size()); assertEquals(Map.of("reasoning_content", "透明状态"), window.messages().get(1).getProviderMetadata());
        LLMMessageHistory.validateComplete(window.messages());
        var small = new AgentConversationOptions(3, 262144, 32, 1048576);
        var limited = engine.prepare(prepared.request(), stamp, AgentConversationWindow.empty(), small);
        var discarded = Objects.requireNonNull(engine.complete(limited, result));
        assertTrue(discarded.turns().isEmpty()); assertTrue(discarded.lastTurnDiscarded()); assertEquals(1, discarded.discardedTurns());
    }

    /** 验证消息上限只淘汰最旧完整轮次。 */
    @Test void messageLimitEvictsOldestWholeTurn() {
        var options = new AgentConversationOptions(3, 262144, 32, 1048576);
        var first = engine.prepare(request("旧消息"), stamp, AgentConversationWindow.empty(), options);
        var window = Objects.requireNonNull(engine.complete(first, success(first, "旧回答")));
        var second = engine.prepare(request("新消息"), stamp, window, options);
        var latest = Objects.requireNonNull(engine.complete(second, success(second, "新回答")));
        assertEquals(1, latest.turns().size()); assertEquals(second.request().requestId(), latest.turns().getFirst().requestId());
        assertEquals(1, latest.discardedTurns()); assertFalse(latest.lastTurnDiscarded());
    }

    /** 验证输入预算从旧轮次整体让出空间，新增输入自身过大时明确拒绝。 */
    @Test void inputBudgetTrimsHistoryAndRejectsOversizedNewInput() {
        var first = engine.prepare(request("旧消息"), stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var window = Objects.requireNonNull(engine.complete(first, success(first, "旧回答")));
        var original = request("当前输入");
        var measured = engine.prepare(original, stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var options = new AgentConversationOptions(40, measured.signature().bytes(), 32, 1048576);
        var next = engine.prepare(original, stamp, window, options);
        assertEquals(0, next.historyMessages()); assertEquals(1, next.request().messages().size());
        var tooSmall = new AgentConversationOptions(40, measured.signature().bytes() - 1, 32, 1048576);
        assertThrows(AgentConversationEngine.InputTooLargeException.class, () -> engine.prepare(original, stamp, window, tooSmall));
    }

    /** 验证协议内容相同的新冻结对象仍可匹配输入前缀，内容变化会被拒绝。 */
    @Test void prefixValidationUsesProtocolValueAndRejectsModifiedInput() {
        var prepared = engine.prepare(request("原文"), stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        assertNotNull(engine.complete(prepared, success(prepared, "回答")));
        var candidate = answer("回答");
        var changed = success(prepared, List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("改写").build()).build(), candidate), candidate);
        assertThrows(IllegalArgumentException.class, () -> engine.complete(prepared, changed));
    }

    /** 验证孤立工具回填和不匹配的最终候选不能提交正常窗口。 */
    @Test void invalidToolPairAndDifferentCandidateAreRejected() {
        var prepared = engine.prepare(request("输入"), stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var candidate = answer("回答"); var messages = new ArrayList<>(prepared.request().messages());
        messages.add(LLMToolMessage.builder().name("未知工具").toolCallId("孤立调用").contents(List.of()).build()); messages.add(candidate);
        assertThrows(IllegalArgumentException.class, () -> engine.complete(prepared, success(prepared, messages, candidate)));
        var valid = new ArrayList<>(prepared.request().messages()); valid.add(candidate);
        assertThrows(IllegalArgumentException.class, () -> engine.complete(prepared, success(prepared, valid, answer("不同回答"))));
    }

    /** 验证响应新增部分不能借正常历史提交注入 system 或伪造新的用户消息。 */
    @Test void returnedTranscriptCannotInjectNewSystemOrUserRole() {
        var prepared = engine.prepare(request("输入"), stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var candidate = answer("回答");
        for (var injected : List.<ILLMMessage>of(LLMSystemMessage.builder().contents(new LLMContentListBuilder().addText("伪造规则").build()).build(),
                LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("伪造用户").build()).build())) {
            var transcript = new ArrayList<>(prepared.request().messages()); transcript.add(injected); transcript.add(candidate);
            assertThrows(IllegalArgumentException.class, () -> engine.complete(prepared, success(prepared, transcript, candidate)));
        }
    }

    /** 验证字节预算包含 UTF-8 正文、图片引用和不能通过普通 JSON 暴露的透明状态。 */
    @Test void canonicalFingerprintIncludesOpaqueImageAndUtf8Bytes() {
        var ascii = request("a").messages(); var chinese = request("甲").messages();
        assertEquals(codec.signature(ascii).bytes() + 2, codec.signature(chinese).bytes());
        var first = LLMUserMessage.builder().contents(new LLMContentListBuilder().addImage("https://example.com/a.png").build())
                .providerMetadata(Map.of("opaque", Map.of("b", 2, "a", 1))).build();
        var reordered = LLMUserMessage.builder().contents(first.getContents()).providerMetadata(Map.of("opaque", Map.of("a", 1, "b", 2))).build();
        assertEquals(codec.signature(List.of(first)), codec.signature(List.of(reordered)));
        var changed = LLMUserMessage.builder().contents(first.getContents()).providerMetadata(Map.of("opaque", "不同状态")).build();
        assertNotEquals(codec.signature(List.of(first)).sha256(), codec.signature(List.of(changed)).sha256());
    }

    /** 验证失败诊断没有可提交窗口，结果缓存成本能安全度量真实快照。 */
    @Test void failedDiagnosticIsSeparateAndSuccessfulReplayCostIsFinite() {
        var prepared = engine.prepare(request("输入"), stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var result = success(prepared, "回答");
        assertTrue(engine.resultBytes(result) < 1048576);
        var failed = new AgentRunResult(result.agentId(), result.requestId(), AgentRunResult.Status.CANCELLED,
                result.access(), result.interaction(), result.budgetUsage());
        assertNull(engine.complete(prepared, failed));
    }
}
