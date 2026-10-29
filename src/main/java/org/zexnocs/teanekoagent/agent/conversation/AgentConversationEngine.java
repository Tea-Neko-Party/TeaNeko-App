package org.zexnocs.teanekoagent.agent.conversation;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentRunResult;
import org.zexnocs.teanekoagent.agent.AgentUserText;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageHistory;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import tools.jackson.databind.ObjectMapper;

import java.io.Serial;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
     * 绑定消息与可选原文凭据指纹，授权原文变化不能取得相同请求的旧结果；会话提示仍由邮箱仲裁。
     * @param request 已冻结的原始请求
     * @return 用于在途冲突及完成重放的身份
     */
    public AgentRequestJournal.Identity identity(AgentRunRequest request) {
        return new AgentRequestJournal.Identity(request.actor(), request.activity(), inputDigest(codec.signature(request.messages()).sha256(), request.userText()));
    }

    /**
     * 将原始文本凭据纳入请求身份，按 Java 字符保留代理项区别；缺失凭据保留既有消息指纹。
     * @param messagesHash 原消息协议摘要
     * @param userText 可选可信原文
     * @return 不含原文的稳定完整输入摘要
     * @throws IllegalStateException 当运行环境缺失标准 SHA-256 算法时
     */
    private static String inputDigest(String messagesHash, @Nullable AgentUserText userText) {
        if (userText == null) { return messagesHash; }
        try {
            var data = "agent-user-text-v1:" + messagesHash + ":" + userText.sourceId().length() + ":" + userText.sourceId() + userText.text();
            var digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < data.length(); i++) { char value = data.charAt(i); digest.update((byte) (value >>> 8)); digest.update((byte) value); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ignored) { throw new IllegalStateException("运行环境缺失标准输入摘要算法。"); }
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
        return prepare(original, stamp, window, options, 0);
    }

    /**
     * 在历史截取前预留已解析人格的协议字节，避免历史占满后才因必需人格额外超限。
     * @param original 当前新增输入
     * @param stamp 实际会话代次
     * @param window 原窗口
     * @param options 本轮策略
     * @param reservedBytes 必需人格完整编码的预留字节
     * @return 原输入和可保留完整旧轮次
     * @throws InputTooLargeException 当新增输入及必需人格自身超限时
     */
    public Prepared prepare(AgentRunRequest original, AgentConversationStamp stamp, AgentConversationWindow window,
            AgentConversationOptions options, long reservedBytes) {
        var name = original.actor().userId().map(id -> "user_" + id.toString().replace("-", "")).orElse("system_task");
        var fresh = original.messages().stream().map(message -> message.getRole() == LLMMessageRole.USER
                ? LLMUserMessage.builder().name(name).contents(message.getContents())
                        .providerMetadata(message.getProviderMetadata()).build() : message).toList();
        var ownBytes = codec.signature(fresh).bytes();
        if (reservedBytes < 0 || reservedBytes >= options.maxInputBytes() || ownBytes > options.maxInputBytes() - reservedBytes) { throw new InputTooLargeException(); }
        var kept = window.trim(options);
        while (!kept.turns().isEmpty() && kept.bytes() > options.maxInputBytes() - reservedBytes - ownBytes) { kept = kept.dropOldest(); }
        var history = kept.messages();
        var messages = new ArrayList<>(history); messages.addAll(fresh);
        var request = new AgentRunRequest(original.requestId(), original.actor(), original.activity(), messages, stamp, null, original.userText(), options.maxInputBytes(), original.personalityIdentity());
        return new Prepared(request, kept, options, history.size(), codec.signature(request.messages()));
    }

    /**
     * 度量向非空原输入插入人格的完整协议增量，包含基础设定、补正信封和分隔成本。
     * @param snapshot 当前已解析人格
     * @return 完整协议字节增量，不等于模型 token
     */
    public long personalityBytes(AgentPersonalitySnapshot snapshot) {
        List<ILLMMessage> probe = List.of(LLMUserMessage.builder().contents(List.of()).build());
        return codec.signature(new AgentPromptInput(probe, List.of(), 0, snapshot).messages()).bytes()
                - codec.signature(probe).bytes();
    }

    /**
     * 仅为完整成功结果生成窗口补丁，分别复核实际模型输入与原始输入，再只保留本轮正常增量。
     * @param prepared 本轮固定装配数据
     * @param result 已校验请求身份的结果
     * @return 成功窗口；非成功结果为空
     * @throws IllegalArgumentException 当结果缺失输入前缀、工具配对或最终候选不一致时
     */
    public @Nullable AgentConversationWindow complete(Prepared prepared, AgentRunResult result) {
        if (result.status() != AgentRunResult.Status.SUCCESS) { return null; }
        var interaction = result.interaction().orElseThrow();
        var transcript = interaction.transcript();
        var projection = result.promptTranscript();
        var expectedPersonality = prepared.request().personalityIdentity();
        if (expectedPersonality != null && (projection == null || projection.input().personality() == null
                || !expectedPersonality.equals(projection.input().personality().identity()))) {
            throw new IllegalArgumentException("成功轮次没有使用邮箱固定的人格身份。");
        }
        if (projection != null) {
            if (!codec.signature(projection.input().original()).equals(prepared.signature())
                    || codec.signature(projection.input().messages()).bytes() > prepared.options().maxInputBytes()) {
                throw new IllegalArgumentException("Prompt 投影与会话原始输入或字节预算不一致。");
            }
            var verified = projection.input().project(interaction, codec);
            if (!codec.signature(verified).equals(codec.signature(projection.messages()))) {
                throw new IllegalArgumentException("Prompt 已验证投影在交接中发生变化。");
            }
            transcript = verified;
        }
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
            var projection = result.promptTranscript();
            if (projection != null) {
                bytes = add(bytes, codec.serializedBytes(projection.input()));
                bytes = add(bytes, codec.signature(projection.messages()).bytes());
            }
            var runtime = result.access().flatMap(AgentAccessDecision::runtime);
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
        @Serial
        private static final long serialVersionUID = 1L;
        /** 创建不回显正文的明确超限原因。 */
        InputTooLargeException() { super("本次新增输入超过会话输入字节上限。"); }
    }
}
