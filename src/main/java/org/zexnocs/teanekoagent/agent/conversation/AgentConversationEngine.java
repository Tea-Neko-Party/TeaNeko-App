package org.zexnocs.teanekoagent.agent.conversation;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentRunResult;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 负责窗口装配、协议提交验证和缓存度量；不持有邮箱锁、执行控制或外部资源。
 * 所有深层冻结和 JSON 工作由邮箱拥有者在锁外调用。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentConversationEngine {
    private final LLMMessageCodec codec;

    /**
     * 保存纯协议处理依赖，不读取配置或创建任务。
     * @param mapper 本地 JSON 转换器
     * @throws NullPointerException 当转换器为空时
     */
    public AgentConversationEngine(ObjectMapper mapper) { codec = new LLMMessageCodec(mapper); }

    /**
     * 绑定原始输入指纹，忽略调用者提供的会话提示，代次由邮箱仲裁。
     * @param request 已冻结的原始请求
     * @return 用于在途冲突及完成重放的身份
     */
    public AgentRequestJournal.Identity identity(AgentRunRequest request) {
        return new AgentRequestJournal.Identity(request.actor(), request.activity(), codec.signature(request.messages()).sha256());
    }

    /**
     * 出队时读取有效窗口，按完整轮次收缩历史，并为新用户输入绑定稳定 UUID 名称。
     * @param original 原始新增输入，不应包含邮箱已有历史
     * @param stamp 邮箱认可的会话代次
     * @param window 当前成功窗口
     * @param options 本轮固定策略
     * @return 实际调用输入与提交所需的不可变数据
     * @throws InputTooLargeException 当新增输入自身已经超限时
     */
    public Prepared prepare(AgentRunRequest original, AgentConversationStamp stamp, AgentConversationWindow window,
            AgentConversationOptions options) {
        var name = original.actor().userId().map(id -> "user_" + id.toString().replace("-", "")).orElse("system_task");
        var fresh = original.messages().stream().map(message -> message.getRole() == LLMMessageRole.USER
                ? (ILLMMessage) LLMUserMessage.builder().name(name).contents(message.getContents())
                        .providerMetadata(message.getProviderMetadata()).build() : message).toList();
        var ownBytes = codec.signature(fresh).bytes();
        if (ownBytes > options.maxInputBytes()) { throw new InputTooLargeException(); }
        var kept = window.trim(options);
        while (!kept.turns().isEmpty() && kept.bytes() > options.maxInputBytes() - ownBytes) { kept = kept.dropOldest(); }
        var history = kept.messages();
        var messages = new ArrayList<>(history); messages.addAll(fresh);
        var request = new AgentRunRequest(original.requestId(), original.actor(), original.activity(), messages, stamp);
        return new Prepared(request, kept, options, history.size(), codec.signature(request.messages()));
    }

    /**
     * 仅为完整成功结果生成窗口补丁，校验完整 Prompt 前缀后只保留本轮新增部分。
     * @param prepared 本轮固定装配数据
     * @param result 已校验请求身份的结果
     * @return 成功窗口；非成功结果为空
     * @throws IllegalArgumentException 当结果缺失输入前缀、工具配对或最终候选不一致时
     */
    public @Nullable AgentConversationWindow complete(Prepared prepared, AgentRunResult result) {
        if (result.status() != AgentRunResult.Status.SUCCESS) { return null; }
        var interaction = result.interaction().orElseThrow();
        var transcript = interaction.transcript();
        int inputSize = prepared.request().messages().size();
        if (transcript.size() <= inputSize || !codec.signature(transcript.subList(0, inputSize)).equals(prepared.signature())) {
            throw new IllegalArgumentException("成功轮次的原始输入前缀缺失或发生变化。");
        }
        if (transcript.subList(inputSize, transcript.size()).stream()
                .anyMatch(message -> message.getRole() != LLMMessageRole.ASSISTANT && message.getRole() != LLMMessageRole.TOOL)) {
            throw new IllegalArgumentException("响应新增部分不能注入系统或用户身份消息。");
        }
        LLMMessageHistory.validateComplete(transcript);
        var last = transcript.getLast();
        if (!(last instanceof ILLMAssistantMessage assistant) || !assistant.getToolCalls().isEmpty()
                || !codec.signature(List.of(last)).equals(codec.signature(List.of(interaction.finalCandidate().orElseThrow())))) {
            throw new IllegalArgumentException("成功轮次的最终候选与完整历史不一致。");
        }
        var delta = transcript.subList(prepared.historyMessages(), transcript.size());
        var turn = new AgentConversationWindow.Turn(result.requestId(), prepared.request().actor(), delta, codec.signature(delta));
        return prepared.window().append(turn, prepared.options());
    }

    /**
     * 保守度量结果正文，额外包含通用 JSON 可能忽略的供应商消息状态；失败时只保留完成状态。
     * @param result 已完成结果
     * @return 饱和字节成本，无法编码时为最大值，不表示精确 Java 堆大小
     */
    public long resultBytes(AgentRunResult result) {
        try {
            long bytes = add(4096, codec.serializedBytes(result.budgetUsage()));
            var runtime = result.access().flatMap(decision -> decision.runtime());
            if (runtime.isPresent()) {
                var options = runtime.orElseThrow().modelOptions().toOptions();
                bytes = add(bytes, codec.serializedBytes(Map.of("provider", options.findProvider().orElse(""),
                        "model", options.findModel().orElse(""), "metadata", options.getMetadata(),
                        "stopWords", options.findStopWords().orElse(List.of()))));
            }
            if (result.interaction().isPresent()) {
                var interaction = result.interaction().orElseThrow();
                bytes = add(bytes, codec.serializedBytes(interaction));
                bytes = add(bytes, codec.signature(interaction.transcript()).bytes());
                for (var attempt : interaction.attempts()) {
                    if (attempt.response().isEmpty()) { continue; }
                    for (var choice : attempt.response().orElseThrow().getChoices()) {
                        var message = choice.getMessage();
                        if (message != null) { bytes = add(bytes, codec.signature(List.of(message)).bytes()); }
                    }
                }
            }
            return bytes;
        } catch (RuntimeException ignored) { return Long.MAX_VALUE; }
    }

    /**
     * 饱和累加字节成本，防止超大诊断重新获得缓存额度。
     * @param first 已计量成本
     * @param second 新增成本
     * @return 饱和值
     */
    private static long add(long first, long second) { return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second; }

    /**
     * 本轮实际输入及提交数据，不包含运行服务或控制。
     * @param request 固定历史和新输入组成的请求
     * @param window 实际采用的完整历史窗口
     * @param options 本轮固定策略
     * @param historyMessages 历史前缀消息数
     * @param signature 实际完整输入的协议指纹
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Prepared(AgentRunRequest request, AgentConversationWindow window, AgentConversationOptions options,
                           int historyMessages, LLMMessageCodec.Signature signature) {}

    /**
     * 新增输入自身超过本轮上限，不能靠移除历史继续调用模型。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static final class InputTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /** 创建不回显正文的明确超限原因。 */
        InputTooLargeException() { super("本次新增输入超过会话输入字节上限。"); }
    }
}
