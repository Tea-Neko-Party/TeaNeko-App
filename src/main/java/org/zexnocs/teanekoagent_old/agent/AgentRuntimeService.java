package org.zexnocs.teanekoagent_old.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.agent.event.*;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentPromptComponent;
import org.zexnocs.teanekoagent_old.agent.thinking.*;
import org.zexnocs.teanekoagent_old.agent.token.AgentTokenMonitorService;
import org.zexnocs.teanekoagent_old.agent.token.AgentTokenUsageRecord;
import org.zexnocs.teanekoagent_old.file_config.interfaces.IAgentFileConfigService;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelService;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent_old.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolService;
import org.zexnocs.teanekoagent_old.tool.AgentToolRegistryService;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekocore.event.interfaces.IEventService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agent 运行时服务。
 * <br>该服务负责处理单轮入站消息，推送 Agent 运行时事件，复用 LLM framework 完成 prompt 构建、模型调用、工具调用回填和出站消息生成。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {
    /**
     * 默认最大工具调用轮数。
     */
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 3;

    /**
     * 默认暴露给模型的工具包。
     */
    private static final String DEFAULT_TOOL_PACKAGE = ILLMToolService.ALL_TOOL_PACKAGES;

    /**
     * Agent 上下文编排服务。
     */
    private final AgentContextService contextService;

    /**
     * LLM 模型调用服务。
     */
    private final LLMModelService llmModelService;

    /**
     * Agent 工具注册表服务。
     */
    private final AgentToolRegistryService toolRegistryService;

    /**
     * Agent token 监控服务。
     */
    private final AgentTokenMonitorService tokenMonitorService;

    /**
     * Agent 受控思考流程服务。
     */
    private final AgentThinkingService thinkingService;

    /**
     * Agent 文件配置服务。
     */
    private final IAgentFileConfigService agentFileConfigService;

    /**
     * core 事件服务。
     */
    private final IEventService eventService;

    /**
     * 处理一条入站消息，并返回平台无关的出站消息。
     *
     * @param context 当前 Agent 会话上下文。
     * @param inbound 宿主适配层传入的入站消息。
     * @return 生成的出站消息；无需回复时为空。
     */
    public Optional<AgentOutput> handle(AgentConversationContext context, ITeaNekoMessageData inbound) {
        return handle(context, inbound, DEFAULT_MAX_TOOL_ROUNDS, DEFAULT_TOOL_PACKAGE);
    }

    /**
     * 使用指定工具轮数上限处理一条入站消息。
     *
     * @param context       当前 Agent 会话上下文。
     * @param inbound       宿主适配层传入的入站消息。
     * @param maxToolRounds 最大工具调用轮数。
     * @return 生成的出站消息；无需回复时为空。
     */
    public Optional<AgentOutput> handle(AgentConversationContext context,
                                        ITeaNekoMessageData inbound,
                                        int maxToolRounds) {
        return handle(context, inbound, maxToolRounds, DEFAULT_TOOL_PACKAGE);
    }

    /**
     * 使用指定运行时选项处理一条入站消息。
     *
     * @param context       当前 Agent 会话上下文。
     * @param inbound       宿主适配层传入的入站消息。
     * @param maxToolRounds 最大工具调用轮数。
     * @param toolPackage   暴露给模型的工具包名称。
     * @return 生成的出站消息；无需回复时为空。
     */
    public Optional<AgentOutput> handle(AgentConversationContext context,
                                        ITeaNekoMessageData inbound,
                                        int maxToolRounds,
                                        String toolPackage) {
        var data = new AgentTurnData(
                requireContext(context),
                requireInbound(inbound),
                maxToolRounds,
                toolPackage
        );
        eventService.pushEventWithFuture(new AgentTurnEvent(data, this)).finish().join();
        return data.findAgentOutput();
    }

    /**
     * 执行 {@link AgentTurnEvent} 未取消时的默认 Agent 单轮运行逻辑。
     * <br>该方法由事件后置逻辑调用，业务代码通常不应直接调用。
     *
     * @param data 可被监听器修改的单轮运行时事件数据。
     */
    public void __handleForEvent(AgentTurnData data) {
        var checkedContext = requireContext(data.getContext());
        var checkedInbound = requireInbound(data.getInboundMessage());
        var userText = inboundText(checkedInbound);
        if (userText.isBlank()) {
            return;
        }

        var personality = checkedContext.getResolvedPersonality();
        if (personality == null) {
            personality = contextService.resolvePersonality(checkedContext);
        }
        if (!personality.config().enabled()) {
            return;
        }

        contextService.appendUser(checkedContext, userText, checkedInbound.getTime());
        contextService.compress(checkedContext);

        var startedAt = Instant.now();
        var loopResult = runModelLoop(
                data,
                checkedContext,
                checkedInbound,
                data.getMaxToolRounds(),
                data.getToolPackage()
        );
        var assistant = Optional.ofNullable(loopResult.assistant());
        assistant.ifPresent(data::setAssistantMessage);
        contextService.recordStagedMemories(checkedContext);
        var output = assistant
                .map(message -> buildAgentOutput(data, checkedContext, loopResult, startedAt))
                .orElse(null);
        data.setAgentOutput(output);
        tokenMonitorService.warnIfNecessary(data);
    }

    /**
     * 执行模型调用与工具回填循环。
     *
     * @param context       当前 Agent 会话上下文。
     * @param inbound       当前轮入站消息。
     * @param maxToolRounds 最大工具调用轮数。
     * @param toolPackage   暴露给模型的工具包名称。
     * @return 最后一条 assistant 消息；模型未返回消息时为空。
     */
    private AgentLoopResult runModelLoop(AgentTurnData data,
                                         AgentConversationContext context,
                                         ITeaNekoMessageData inbound,
                                         int maxToolRounds,
                                         String toolPackage) {
        var config = agentFileConfigService.getMainConfig();
        var thinkingEnabled = config.isThinkingEnabled();
        var maxSteps = thinkingEnabled
                ? config.normalizedMaxThinkingSteps()
                : Math.max(1, maxToolRounds + 1);
        var maxSummaryLength = config.normalizedMaxThoughtSummaryLength();
        var allowedToolRounds = Math.clamp(maxToolRounds, 0, Math.max(0, maxSteps - 1));
        var thoughts = new ArrayList<AgentThoughtStep>();
        var modelCalls = 0;
        var toolCalls = 0;
        var model = "";
        var finishReason = "";
        var confidence = 0.0;
        var stepLimitReached = false;

        for (var step = 0; step < maxSteps; step++) {
            var finalOnly = step >= allowedToolRounds;
            var components = new ArrayList<>(runtimeComponents(context, inbound));
            if (thinkingEnabled) {
                components.add(thinkingService.buildPromptComponent(
                        step + 1,
                        maxSteps,
                        maxSummaryLength,
                        finalOnly
                ));
            }
            var prompt = configurePrompt(
                    contextService.buildPrompt(context, "", components),
                    toolPackage,
                    !finalOnly,
                    thinkingEnabled
            );
            var result = callModel(data, context, inbound, step, prompt).orElse(null);
            if (result == null) {
                return new AgentLoopResult(
                        null, thoughts, modelCalls, toolCalls, model, finishReason,
                        confidence, stepLimitReached, thinkingEnabled
                );
            }
            modelCalls++;
            model = safe(result.getModel());
            finishReason = result.getFirstChoice()
                    .map(choice -> safe(choice.getFinishReason()))
                    .orElse("");

            var assistant = result.getFirstMessage().orElse(null);
            if (assistant == null) {
                return new AgentLoopResult(
                        null, thoughts, modelCalls, toolCalls, model, finishReason,
                        confidence, stepLimitReached, thinkingEnabled
                );
            }

            var currentToolCalls = assistant.getToolCalls() == null
                    ? List.<ILLMToolCall>of()
                    : assistant.getToolCalls();
            var rawText = assistantText(assistant);
            var decision = thinkingEnabled
                    ? thinkingService.parseDecision(rawText, !currentToolCalls.isEmpty(), maxSummaryLength)
                    : plainDecision(rawText, !currentToolCalls.isEmpty());
            confidence = decision.getConfidence();
            if (thinkingEnabled) {
                appendDecisionThought(thoughts, decision, currentToolCalls.isEmpty());
            }

            var responseTime = result.getCreated() == null ? Instant.now() : result.getCreated();
            if (currentToolCalls.isEmpty()) {
                var normalizedAssistant = normalizeFinalAssistant(assistant, decision.getAnswer());
                context.addMessage(normalizedAssistant, responseTime);
                return new AgentLoopResult(
                        normalizedAssistant, thoughts, modelCalls, toolCalls, model, finishReason,
                        confidence, stepLimitReached, thinkingEnabled
                );
            }

            if (finalOnly) {
                stepLimitReached = true;
                var normalizedAssistant = normalizeFinalAssistant(assistant, decision.getAnswer());
                context.addMessage(normalizedAssistant, responseTime);
                return new AgentLoopResult(
                        normalizedAssistant, thoughts, modelCalls, toolCalls, model, finishReason,
                        confidence, true, thinkingEnabled
                );
            }

            context.addMessage(assistant, responseTime);
            for (var toolCall : currentToolCalls) {
                var toolResult = callTool(context, inbound, step, toolCall);
                context.addToolMessage(toolCallId(toolCall), toolResult);
                toolCalls++;
                if (thinkingEnabled) {
                    thoughts.add(thinkingService.toolObservation(
                            thoughts.size() + 1,
                            toolCall.getName(),
                            !"Tool execution cancelled.".equals(toolResult)
                    ));
                }
            }
            contextService.compress(context);
            if (step + 1 >= maxSteps - 1) {
                stepLimitReached = true;
            }
        }
        return new AgentLoopResult(
                null, thoughts, modelCalls, toolCalls, model, finishReason,
                confidence, stepLimitReached, thinkingEnabled
        );
    }

    /**
     * 将当前可见工具列表写入 Prompt options。
     *
     * @param prompt      原始 Prompt。
     * @param toolPackage 暴露给模型的工具包名称。
     * @return 写入工具列表后的 Prompt；没有可见工具时返回原 Prompt。
     */
    private LLMPrompt configurePrompt(LLMPrompt prompt,
                                      String toolPackage,
                                      boolean allowTools,
                                      boolean structuredOutput) {
        var options = LLMModelOptions.copyOf(prompt.getOptions());
        if (structuredOutput) {
            options.setResponseFormat(LLMResponseFormat.JSON);
        }
        if (!allowTools) {
            options.setTools(List.of());
            options.setToolChoice("none");
            return new LLMPrompt(prompt.getMessages(), options);
        }
        var tools = toolRegistryService.getToolList(toolPackage);
        if (!tools.isEmpty()) {
            options.setTools(tools);
            if (options.findToolChoice().filter(value -> !value.isBlank()).isEmpty()) {
                options.setToolChoice("auto");
            }
        }
        return new LLMPrompt(prompt.getMessages(), options);
    }

    /**
     * 通过 {@link AgentModelCallEvent} 执行一次模型调用。
     *
     * @param context 当前 Agent 会话上下文。
     * @param inbound 当前轮入站消息。
     * @param round   当前 tool call loop 轮次。
     * @param prompt  本次模型调用的 Prompt。
     * @return 模型调用结果；事件被取消且未写入替代结果时为空。
     */
    private Optional<ILLMResult> callModel(AgentTurnData turnData,
                                           AgentConversationContext context,
                                           ITeaNekoMessageData inbound,
                                           int round,
                                           LLMPrompt prompt) {
        var data = new AgentModelCallData(context, inbound, round, prompt);
        var event = new AgentModelCallEvent(data, llmModelService);
        try {
            eventService.pushEventWithFuture(event).finish().join();
            tokenMonitorService.recordModelCall(turnData, data);
            return data.findResult();
        } catch (RuntimeException exception) {
            tokenMonitorService.recordModelCallException(turnData, data, exception);
            throw exception;
        }
    }

    /**
     * 通过 {@link AgentToolCallEvent} 执行一次工具调用。
     *
     * @param context  当前 Agent 会话上下文。
     * @param inbound  当前轮入站消息。
     * @param round    当前 tool call loop 轮次。
     * @param toolCall 模型请求执行的工具调用。
     * @return 工具执行结果。
     */
    private String callTool(AgentConversationContext context,
                            ITeaNekoMessageData inbound,
                            int round,
                            ILLMToolCall toolCall) {
        var data = new AgentToolCallData(context, inbound, round, toolCall);
        var event = new AgentToolCallEvent(data, toolRegistryService);
        eventService.pushEventWithFuture(event).finish().join();
        return data.findResult().orElse("Tool execution cancelled.");
    }

    /**
     * 将 assistant 消息转换为平台无关出站消息，并推送出站消息事件。
     *
     * @param context   当前 Agent 会话上下文。
     * @param inbound   当前轮入站消息。
     * @param assistant 模型生成的 assistant 消息。
     * @return 平台无关出站消息；assistant 无可见文本或事件取消回复时为空。
     */
    /**
     * 构建本轮运行时附加 Prompt 组件。
     *
     * @param context 当前 Agent 会话上下文。
     * @param inbound 当前轮入站消息。
     * @return 附加 Prompt 组件列表。
     */
    private List<AgentPromptComponent> runtimeComponents(AgentConversationContext context,
                                                         ITeaNekoMessageData inbound) {
        var components = new ArrayList<AgentPromptComponent>();
        components.add(new AgentPromptComponent("runtime-request-context", 15, """
                scopeId: %s
                agentId: %s
                userId: %s
                requestMessageId: %s
                """.formatted(
                context.getScopeId(),
                context.getAgentId(),
                context.getUserId(),
                inbound.getMessageId()
        )));
        components.add(new AgentPromptComponent("tool-rules", 16, """
                Use tools only through the available function tool schema.
                Never invent tool results.
                After requesting a tool call, wait for the tool message result before relying on it.
                Use memory tools only for stable user facts, preferences, relationship details, or explicit memory queries.
                For past-event questions, resolve relative time from temporal-context and call query_memory_by_time.
                Use timePoint for exact time or a single calendar date. Use rangeStart and rangeEnd for vague expressions such as about a week ago.
                All memory tool time parameters must use ISO-8601. Do not pass natural-language time expressions as query boundaries.
                """));
        return List.copyOf(components);
    }

    /**
     * 将普通模型文本转换为未启用结构化思考时的兼容决策。
     *
     * @param text         assistant 文本
     * @param hasToolCalls 是否包含工具调用
     * @return 兼容模型决策
     */
    private static AgentModelDecision plainDecision(String text, boolean hasToolCalls) {
        var decision = new AgentModelDecision();
        if (!hasToolCalls) {
            decision.setAnswer(safe(text));
        }
        return decision;
    }

    /**
     * 将模型提供的可公开思考摘要加入本轮输出。
     *
     * @param thoughts 当前思考摘要列表
     * @param decision 当前模型决策
     * @param finalStep 是否为最终回答步骤
     */
    private static void appendDecisionThought(List<AgentThoughtStep> thoughts,
                                              AgentModelDecision decision,
                                              boolean finalStep) {
        var summary = safe(decision.getThoughtSummary());
        if (summary.isBlank()) {
            summary = finalStep
                    ? "已结合当前上下文和可用信息完成最终校验。"
                    : "已判断当前回答需要先获取额外信息。";
        }
        thoughts.add(new AgentThoughtStep(
                thoughts.size() + 1,
                finalStep ? AgentThoughtPhase.FINAL_CHECK : AgentThoughtPhase.ANALYSIS,
                summary
        ));
    }

    /**
     * 创建只包含最终用户答案的 assistant 消息。
     *
     * @param source 原始 assistant 消息
     * @param answer 最终用户答案
     * @return 可写入长期会话上下文的 assistant 消息
     */
    private static ILLMAssistantMessage normalizeFinalAssistant(ILLMAssistantMessage source, String answer) {
        return LLMAssistantMessage.builder()
                .name(safe(source.getName()))
                .contents(LLMContentListBuilder.builder().addText(safe(answer)).build())
                .toolCalls(List.of())
                .build();
    }

    /**
     * 根据本轮运行结果创建结构化 Agent 输出。
     *
     * @param data      本轮事件数据
     * @param context   会话上下文
     * @param loop      模型与工具循环结果
     * @param startedAt 本轮开始时间
     * @return 结构化 Agent 输出
     */
    private AgentOutput buildAgentOutput(AgentTurnData data,
                                         AgentConversationContext context,
                                         AgentLoopResult loop,
                                         Instant startedAt) {
        var records = data.snapshotTokenUsageRecords();
        var promptTokens = records.stream().mapToInt(AgentTokenUsageRecord::getPromptTokens).sum();
        var completionTokens = records.stream().mapToInt(AgentTokenUsageRecord::getCompletionTokens).sum();
        var totalTokens = records.stream().mapToInt(AgentTokenUsageRecord::getTotalTokens).sum();
        var reasoningTokens = records.stream().mapToInt(AgentTokenUsageRecord::getReasoningTokens).sum();
        var provider = records.stream()
                .map(record -> safe(record.getProvider()))
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> second)
                .orElse("");
        var model = loop.model().isBlank()
                ? records.stream()
                    .map(record -> safe(record.getModel()))
                    .filter(value -> !value.isBlank())
                    .reduce((first, second) -> second)
                    .orElse("")
                : loop.model();
        var personality = context.getResolvedPersonality();
        var personalitySource = personality == null ? "" : personality.source().name();
        var memoryCount = personality == null ? 0 : personality.memories().size();
        var metadata = new AgentOutputMetadata(
                context.getConversationId(),
                context.getAgentId(),
                personalitySource,
                provider,
                model,
                loop.finishReason(),
                startedAt,
                Instant.now(),
                loop.modelCalls(),
                loop.toolCalls(),
                promptTokens,
                completionTokens,
                totalTokens,
                reasoningTokens,
                context.snapshotMessages().size(),
                memoryCount,
                loop.thinkingEnabled(),
                loop.stepLimitReached(),
                loop.confidence()
        );
        var thoughts = agentFileConfigService.getMainConfig().isIncludeThoughtsInOutput()
                ? loop.thoughts()
                : List.<AgentThoughtStep>of();
        return new AgentOutput(thoughts, assistantText(loop.assistant()), metadata);
    }

    /**
     * 从 assistant 消息中提取可见文本。
     *
     * @param message assistant 消息。
     * @return 提取出的可见文本。
     */
    private static String assistantText(ILLMAssistantMessage message) {
        if (message == null) {
            return "";
        }
        var contents = message.getContents();
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        var text = new StringBuilder();
        for (var content : contents) {
            if (content != null && content.getContentPart() instanceof TextLLMContentPart part) {
                appendText(text, part.getText());
            }
        }
        return text.toString().trim();
    }

    /**
     * 向字符串构建器追加非空文本。
     *
     * @param target 目标字符串构建器。
     * @param text   待追加文本。
     */
    private static void appendText(StringBuilder target, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(text.trim());
    }

    /**
     * 获取工具调用回填时使用的 tool call ID。
     *
     * @param toolCall 工具调用。
     * @return tool call ID；模型未提供 ID 时回退为工具名称。
     */
    private static String toolCallId(ILLMToolCall toolCall) {
        if (toolCall.getId() != null && !toolCall.getId().isBlank()) {
            return toolCall.getId();
        }
        return toolCall.getName() == null ? "" : toolCall.getName();
    }

    /**
     * 规范化字符串。
     *
     * @param value 原始字符串
     * @return 非空字符串
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Agent 单轮模型与工具循环结果。
     *
     * @param assistant       最终 assistant 消息
     * @param thoughts        可公开思考摘要
     * @param modelCalls      模型调用次数
     * @param toolCalls       工具调用次数
     * @param model           最后响应模型名称
     * @param finishReason    最后结束原因
     * @param confidence      最终答案置信度
     * @param stepLimitReached 是否因步骤预算进入最终回答
     * @param thinkingEnabled 是否启用结构化思考
     */
    private record AgentLoopResult(
            ILLMAssistantMessage assistant,
            List<AgentThoughtStep> thoughts,
            int modelCalls,
            int toolCalls,
            String model,
            String finishReason,
            double confidence,
            boolean stepLimitReached,
            boolean thinkingEnabled
    ) {
        /**
         * 创建不可变循环结果。
         */
        private AgentLoopResult {
            thoughts = thoughts == null ? List.of() : List.copyOf(thoughts);
            model = safe(model);
            finishReason = safe(finishReason);
        }
    }

    /**
     * 校验并返回非空 Agent 会话上下文。
     *
     * @param context 待校验上下文。
     * @return 非空 Agent 会话上下文。
     */
    private static AgentConversationContext requireContext(AgentConversationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Agent conversation context must not be null");
        }
        return context;
    }

    /**
     * 校验并返回非空入站消息。
     *
     * @param inbound 待校验入站消息。
     * @return 非空入站消息。
     */
    private static ITeaNekoMessageData requireInbound(ITeaNekoMessageData inbound) {
        if (inbound == null) {
            throw new IllegalArgumentException("Agent inbound message must not be null");
        }
        return inbound;
    }

    /**
     * 提取 TeaNeko 消息中的全部文本内容。
     *
     * @param inbound TeaNeko 入站消息
     * @return 合并后的文本内容
     */
    private static String inboundText(ITeaNekoMessageData inbound) {
        var text = new StringBuilder();
        for (var content : inbound.getMessages()) {
            if (content != null && content.getContentPart() instanceof ITextTeaNekoContentPart part) {
                appendText(text, part.getText());
            }
        }
        return text.toString().trim();
    }
}
