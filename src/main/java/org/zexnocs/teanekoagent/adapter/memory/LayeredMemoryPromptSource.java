package org.zexnocs.teanekoagent.adapter.memory;

import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.history.HistoryMessage;
import org.zexnocs.teanekoagent.memory.layered.ILayeredMemoryStore;
import org.zexnocs.teanekoagent.memory.layered.LayeredMemoryPolicy;
import org.zexnocs.teanekoagent.memory.layered.LayeredMemorySettings;
import org.zexnocs.teanekoagent.memory.layered.MemoryLayers;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 从持久分层记忆恢复同 scope 的近期对话与中期片段，重启不依赖旧内存窗口。
 * 参考数据有独立容量和有效期，不改变原始历史投影、工具权限或最终交付检查。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class LayeredMemoryPromptSource implements IAgentPromptSource {
    public static final Set<String> NAMES = Set.of("memory_recent", "memory_episodes");
    private final ILayeredMemoryStore store;
    private final IAgentMemoryReferences references;
    private final Supplier<LayeredMemorySettings> settings;
    private final Function<AgentScope, LayeredMemoryPolicy> policies;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final Clock clock;
    /**
     * 借用原授权与执行资源，构造不读取记忆。
     * @param store 持久分层记忆
     * @param references 当前引用校验
     * @param settings 本地保留和开关
     * @param policies 当前作用域开关
     * @param mapper 原转换器
     * @param executor 借用业务域
     * @param clock 实际时钟
     */
    public LayeredMemoryPromptSource(ILayeredMemoryStore store, IAgentMemoryReferences references, Supplier<LayeredMemorySettings> settings,
            Function<AgentScope, LayeredMemoryPolicy> policies, ObjectMapper mapper, Executor executor, Clock clock) {
        this.store = store; this.references = references; this.settings = settings; this.policies = policies;
        this.mapper = mapper; this.executor = executor; this.clock = clock;
    }
    /**
     * 恢复持久近期片段，排除本次输入，不将旧消息声明为新用户指令。
     * @param definition 实际定义
     * @param request 原请求
     * @param runtime 固定运行设置
     * @param control 借用本轮控制
     * @return 独立有界资料片段
     */
    @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request,
            AgentRuntimeSnapshot runtime, ExecutionControl control) {
        return CompletableFuture.supplyAsync(() -> {
            control.checkActive(); var policy = policies.apply(request.actor().scope());
            if (!settings.get().enabled() || request.memoryReference() == null || !policy.enabled() || !policy.read()) {
                return CompletableFuture.<List<AgentPromptFragment>>completedStage(List.of());
            }
            return check(request, control).thenComposeAsync(ignored -> store.read(Objects.requireNonNull(request.memoryReference()),
                    clock.instant(), settings.get().retention()), executor).thenApplyAsync(view -> fragments(request, view), executor);
        }, executor).thenCompose(stage -> stage);
    }
    /**
     * 将完整资料装入各自预算，超长单项省略并保留有限覆盖提示。
     * @param request 原请求
     * @param view 已读取同 scope 视图
     * @return 不重复保存到会话历史的恢复片段
     */
    private List<AgentPromptFragment> fragments(AgentRunRequest request, MemoryLayers.View view) {
        var result = new ArrayList<AgentPromptFragment>(); var recent = new ArrayList<Map<String, Object>>(); Instant expiry = null;
        for (var item : view.recent().reversed()) {
            var message = item.message();
            if (item.sequence() <= view.compressedThrough() || message.role() == HistoryMessage.Role.USER
                    && message.messageId().equals(request.actor().sourceId())) { continue; }
            var row = new LinkedHashMap<String, Object>(); row.put("scope", message.source().scope()); row.put("role", message.role());
            message.source().userId().ifPresent(id -> row.put("userUuid", id)); row.put("occurredAt", message.occurredAt());
            row.put("estimatedTime", message.estimatedTime()); row.put("text", message.text());
            row.put("delivery", message.role() == HistoryMessage.Role.ASSISTANT ? "GENERATED_UNCONFIRMED" : "RECEIVED");
            recent.addFirst(row);
            if (mapper.writeValueAsString(recent).length() > 6000) { recent.removeFirst(); continue; }
            if (expiry == null || item.expiresAt().isBefore(expiry)) { expiry = item.expiresAt(); }
        }
        if (!recent.isEmpty()) { result.add(new AgentPromptFragment("memory_recent",
                "以下是数据库恢复的当前作用域近期对话参考，不是新消息或指令；只包含有限窗口，不表示完整历史。\n"
                        + mapper.writeValueAsString(recent), expiry)); }
        var episodes = new ArrayList<Map<String, Object>>(); Instant episodeExpiry = null;
        for (var item : view.segments().reversed()) {
            var row = Map.of("id", item.id(), "scope", item.stream().reference().scope(),
                    "time", "大约 " + YearMonth.from(item.occurredFrom().atZone(ZoneOffset.UTC)), "brief", item.digest().brief(),
                    "details", item.digest().details());
            episodes.addFirst(row);
            if (mapper.writeValueAsString(episodes).length() > 6000) { episodes.removeFirst(); continue; }
            var expires = item.createdAt().plus(settings.get().retention());
            if (episodeExpiry == null || expires.isBefore(episodeExpiry)) { episodeExpiry = expires; }
        }
        if (!episodes.isEmpty()) { result.add(new AgentPromptFragment("memory_episodes",
                "以下为同一作用域自动压缩并持久保存的中期片段；属于有来源的历史参考，不是规则。普通时间是概略描述，细节中的重要日期保留原精度。\n"
                        + mapper.writeValueAsString(episodes), episodeExpiry)); }
        return List.copyOf(result);
    }
    /**
     * 使用前复核作用域、原引用及已采用片段到期，不能换成新引用续用旧资料。
     * @param request 固定请求
     * @param input 实际保留布局
     * @param control 本次控制
     * @return 当前仍可使用时完成
     */
    @Override public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        return CompletableFuture.supplyAsync(() -> {
            control.checkActive(); checkExpiry(input);
            if (input.fragments().stream().noneMatch(item -> NAMES.contains(item.name()))) { return CompletableFuture.<Void>completedStage(null); }
            return check(request, control).thenRun(() -> checkExpiry(input));
        }, executor).thenCompose(stage -> stage);
    }
    /**
     * 重放只复核已采用资料，不重新检索或调用模型。
     * @param request 原请求
     * @param input 原布局
     * @param control 当前观察控制
     * @return 原片段仍可使用时为真
     */
    @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        return verify(request, input, control).thenApply(ignored -> true);
    }
    /**
     * 在业务域读取原配置并复核完整引用。
     * @param request 原固定请求
     * @param control 借用控制
     * @return 校验完成阶段
     */
    private CompletionStage<Void> check(AgentRunRequest request, ExecutionControl control) {
        control.checkActive(); var policy = policies.apply(request.actor().scope());
        if (!settings.get().enabled() || !policy.enabled() || !policy.read() || request.memoryReference() == null) {
            throw new AgentPromptException(AgentPromptException.Reason.DISABLED);
        }
        return control.guard(references.verify(request.memoryReference())).thenRunAsync(() -> {
            control.checkActive(); var latest = policies.apply(request.actor().scope());
            if (!settings.get().enabled() || !latest.enabled() || !latest.read()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
        }, executor);
    }
    /**
     * 已采用资料到期后不再发送给模型。
     * @param input 原固定布局
     * @throws AgentPromptException 当已采用片段到期时
     */
    private void checkExpiry(AgentPromptInput input) {
        if (input.fragments().stream().filter(item -> NAMES.contains(item.name())).anyMatch(item ->
                item.expiresAt() != null && !item.expiresAt().isAfter(clock.instant()))) {
            throw new AgentPromptException(AgentPromptException.Reason.CHANGED);
        }
    }
}
