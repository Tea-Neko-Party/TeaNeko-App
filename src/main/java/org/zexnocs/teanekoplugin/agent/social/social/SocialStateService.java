package org.zexnocs.teanekoplugin.agent.social.social;

import org.zexnocs.teanekoplugin.agent.cognition.CognitionContext;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。


import org.springframework.stereotype.Service;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 从真实来信观察互动；情感评估使用本轮证据、限幅、冷却和永久来源防重。
 * <br>2026/11/06：不因刷消息自动增加好感；短期情绪回归平静，关系不因沉默自动惩罚。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.social")
public final class SocialStateService {
    private final ISocialStateStore store;
    private final SocialAccess access;
    private final IAgentMemoryReferences references;

    /**
     * @param store 事务状态
     * @param access 当前组件配置
     * @param references 当前空间复核
     */
    public SocialStateService(ISocialStateStore store, SocialAccess access, IAgentMemoryReferences references) {
        this.store = store;
        this.access = access;
        this.references = references;
    }

    /**
     * @param request 真实请求
     * @return 社交组件当前是否可以处理该用户
     */
    public boolean enabled(AgentRunRequest request) {
        return request.actor().origin() == AgentActorContext.Origin.USER && CognitionContext.reference(request.memoryReference()) != null
                && access.readable(request.actor().scope()) && (access.settings(request.actor().scope(), "affinity").isPresent()
                || access.settings(request.actor().scope(), "mood").isPresent());
    }

    /**
     * @param request 当前真实请求
     * @return 来信状态；重复解析 Prompt 不会重置关怀计数
     */
    public CompletionStage<SocialState> observe(AgentRunRequest request) {
        var reference = Objects.requireNonNull(CognitionContext.reference(request.memoryReference()));
        var user = request.actor().userId().orElseThrow();
        var key = StableDigest.sha256(request.actor().scope().clientId(), request.actor().scope().scopeId(), request.actor().sourceId());
        int initial = initial(request);
        var now = Instant.now();
        return references.verify(reference).thenCompose(ignored -> store.update(reference, user, initial, "received:" + key, now, old ->
                new SocialState(old.affinity(), old.mood(), old.moodAt(), old.appraisedAt(), old.evidenceHash(), now, key,
                        old.lastCare(), 0, "", now, old.revision() + 1)));
    }

    /**
     * @param request 真实请求
     * @return 当前配置的初始亲近程度
     */
    private int initial(AgentRunRequest request) {
        return access.settings(request.actor().scope(), "affinity").map(value -> value.path("initial").asInt(30)).orElse(30);
    }

    /**
     * @param kind 模型判断的互动性质
     * @param quote 本轮连续证据
     * @param context 真实工具上下文
     * @return 实际更新结果，普通问候及重复证据不能刷分
     */
    public CompletionStage<String> appraise(Kind kind, String quote, TeaNekoAgentToolContext context) {
        var request = context.request();
        var control = context.requireInvocation().getControl();
        context.requireCall();
        if (!enabled(request)) { return CompletableFuture.completedStage("当前未装配可用的关系或心情组件。"); }
        if (request.userText() == null || quote.length() < 2 || quote.length() > 512
                || !request.userText().text().contains(quote) || lowInformation(quote)) {
            return CompletableFuture.completedStage("没有本轮有效互动证据；普通重复问候不会改变关系或心情。");
        }
        var scope = request.actor().scope();
        var affinity = access.settings(scope, "affinity");
        var mood = access.settings(scope, "mood");
        int positive = affinity.map(value -> value.path("positiveStep").asInt(2)).orElse(0);
        int negative = affinity.map(value -> value.path("negativeStep").asInt(3)).orElse(0);
        int cooldown = affinity.map(value -> value.path("cooldownMinutes").asInt(60)).orElse(15);
        int decay = mood.map(value -> value.path("decayHours").asInt(12)).orElse(12);
        var now = Instant.now();
        var key = StableDigest.sha256(scope.clientId(), scope.scopeId(), request.actor().sourceId());
        var evidence = StableDigest.sha256(quote.strip().toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]", ""));
        return control.guard(references.verify(Objects.requireNonNull(CognitionContext.reference(request.memoryReference()))))
                .thenCompose(ignored -> store.update(CognitionContext.reference(request.memoryReference()), request.actor().userId().orElseThrow(), initial(request),
                        "appraised:" + key, now, old -> {
                            if (!access.settings(scope, "affinity").equals(affinity) || !access.settings(scope, "mood").equals(mood)) {
                                throw new IllegalStateException("情感组件参数已变化，本次评估停止。");
                            }
                            if (!enabled(request) || evidence.equals(old.evidenceHash())
                                    || now.isBefore(old.appraisedAt().plus(Duration.ofMinutes(cooldown)))) { return old; }
                            int change = switch (kind) { case AFFECTION -> positive; case REPAIR -> Math.min(1, positive); case HURT -> -negative; case BOUNDARY -> 0; };
                            int feeling = switch (kind) { case AFFECTION -> 20; case REPAIR -> 15; case HURT -> -30; case BOUNDARY -> -5; };
                            return new SocialState(Math.clamp(old.affinity() + change, 0, 100),
                                    mood.isEmpty() ? old.mood() : Math.clamp(old.currentMood(now, decay) + feeling, -100, 100),
                                    mood.isEmpty() ? old.moodAt() : now, now, evidence, old.lastUser(), old.sourceKey(),
                                    old.lastCare(), old.reminders(), old.pending(), old.nextCare(), old.revision() + 1);
                        })).thenApply(state -> "已按本轮证据检查关系变化；亲近度=" + state.affinity()
                        + "，心情=" + state.currentMood(now, decay) + "。重复证据和冷却内评估不会累加，不向用户报告内部数值。");
    }

    /**
     * @param quote 当前证据
     * @return 没有新增互动含义的寒暄或单词重复
     */
    static boolean lowInformation(String quote) {
        var value = quote.strip().toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]", "");
        return value.isBlank() || value.matches("(?:你好|您好|早安|晚安|在吗|哈哈|hi|hello|hey|嗯|喵)+" )
                || value.matches("(.{1,3})\\1{2,}");
    }

    /**
     * 有明确本轮证据的互动性质，不允许任意数值或权限变化。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.9.0-l
     */
    public enum Kind { AFFECTION, REPAIR, HURT, BOUNDARY }
}
