package org.zexnocs.teanekoplugin.agent.social.care;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor.Delivery;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryMessage;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.social.contact.IPrivateContactTransport;
import org.zexnocs.teanekoplugin.agent.social.proactive.IProactiveContextSource;
import org.zexnocs.teanekoplugin.agent.social.proactive.adapter.ProactiveTargets;
import org.zexnocs.teanekoplugin.agent.social.social.ISocialStateStore;
import org.zexnocs.teanekoplugin.agent.social.social.SocialAccess;
import org.zexnocs.teanekoplugin.agent.social.social.SocialState;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 以真实私聊互动和关系状态决定是否值得调用主动模型，发送前再次检查用户是否已经回复。
 * <br>2026/11/06：占用与计数持久化；弃言不记为发言，未知结果暂停到用户再次联系。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.social")
public final class CareContextSource implements IProactiveContextSource {
    private final SocialAccess access;
    private final ISocialStateStore states;
    private final IPrivateContactTransport transport;
    private final IAgentMemoryReferences references;
    private final ILayeredMemoryStore history;
    private final IAgentDeliveryStore deliveries;

    /**
     * @param access 配置
     * @param states 状态
     * @param transport 真实用户映射
     * @param references 空间
     * @param history 原消息
     * @param deliveries 回执
     */
    public CareContextSource(SocialAccess access, ISocialStateStore states, IPrivateContactTransport transport,
            IAgentMemoryReferences references, ILayeredMemoryStore history, IAgentDeliveryStore deliveries) {
        this.access = access;
        this.states = states;
        this.transport = transport;
        this.references = references;
        this.history = history;
        this.deliveries = deliveries;
    }

    /**
     * @param target 原目标
     * @param kind 类型
     * @return 仅已装配私聊定时关怀参与
     */
    @Override public boolean applies(ProactiveTargets.Target target, AgentTriggerGate.Kind kind) {
        return kind == AgentTriggerGate.Kind.TIMER && !target.group() && access.settings(target.scope(), "care").isPresent();
    }

    /**
     * @param target 原目标
     * @param reference 原空间
     * @param request 原发生
     * @param control 借用控制
     * @return 原子取得的关怀候选
     */
    @Override public CompletionStage<Optional<IProactiveContextSource.ISession>> prepare(ProactiveTargets.Target target, MemoryNamespaceReference reference,
            UUID request, ExecutionControl control) {
        var config = access.settings(target.scope(), "care");
        var affinity = access.settings(target.scope(), "affinity");
        var user = transport.user(target.scope(), target.id());
        if (target.group() || config.isEmpty() || affinity.isEmpty() || user.isEmpty() || !access.readable(target.scope())) {
            return CompletableFuture.completedStage(Optional.empty());
        }
        int initial = affinity.get().path("initial").asInt(30);
        var now = Instant.now();
        return control.guard(references.verify(reference)).thenCompose(ignored -> activity(reference, user.get(), now))
                .thenCompose(activity -> {
                    if (activity.isEmpty()) { return CompletableFuture.completedStage(Optional.empty()); }
                    var recent = activity.get();
                    return states.update(reference, user.get(), initial, "", now, original -> {
                        var old = original;
                        if (recent.userAt().isAfter(old.lastUser())) {
                            old = new SocialState(old.affinity(), old.mood(), old.moodAt(), old.appraisedAt(), old.evidenceHash(),
                                    recent.userAt(), recent.userKey(), old.lastCare(), 0, "", recent.userAt(), old.revision() + 1);
                        }
                        if (!due(old, recent, config.get(), now)) { return old; }
                        return new SocialState(old.affinity(), old.mood(), old.moodAt(), old.appraisedAt(), old.evidenceHash(),
                                old.lastUser(), old.sourceKey(), old.lastCare(), old.reminders(), request.toString(),
                                now.plus(Duration.ofHours(config.get().path("cooldownHours").asInt(24))), old.revision() + 1);
                    }).thenApply(state -> state.pending().equals(request.toString())
                            ? Optional.of(new CareSession(reference, user.get(), target, state, recent, config.get(), initial))
                            : Optional.empty());
                });
    }

    /**
     * @param state 当前状态
     * @param activity 已确认互动
     * @param config 当前阈值
     * @param now 当前时间
     * @return 是否值得产生模型候选
     */
    static boolean due(SocialState state, Activity activity, JsonNode config, Instant now) {
        if (!state.pending().isEmpty() || state.affinity() < config.path("minimumAffinity").asInt(70)
                || state.reminders() >= config.path("maxReminders").asInt(2) || now.isBefore(state.nextCare())) { return false; }
        // 2026/11/06：同空间群聊中的新来信同样代表用户仍在联系，不能被更早的私聊覆盖。
        var lastUser = state.lastUser().isAfter(activity.userAt()) ? state.lastUser() : activity.userAt();
        boolean unanswered = activity.replyAt().isAfter(lastUser);
        int hours = config.path(unanswered ? "unansweredHours" : "missingHours").asInt(unanswered ? 6 : 48);
        var anchor = unanswered ? activity.replyAt() : lastUser;
        return !now.isBefore(anchor.plus(Duration.ofHours(hours)));
    }

    /**
     * @param reference 原私聊
     * @param user 真实用户
     * @param now 当前时间
     * @return 最近来信与有回执的回复时间
     */
    private CompletionStage<Optional<Activity>> activity(MemoryNamespaceReference reference, UUID user, Instant now) {
        return history.recent(reference, now).thenCompose(messages -> {
            var incoming = messages.stream().filter(value -> value.message().role() == HistoryMessage.Role.USER
                    && value.message().source().userId().filter(user::equals).isPresent()).max(Comparator.comparingLong(MemoryLayers.Message::sequence));
            if (incoming.isEmpty()) { return CompletableFuture.completedStage(Optional.empty()); }
            var latest = incoming.get().message();
            var outgoing = new LinkedHashMap<UUID, Instant>();
            for (var item : messages) {
                if (item.message().role() != HistoryMessage.Role.ASSISTANT) { continue; }
                try { outgoing.put(UUID.fromString(item.message().messageId()), item.message().occurredAt()); }
                catch (IllegalArgumentException ignored) { /* 旧非请求标识没有可核验的发送回执。 */ }
            }
            return deliveries.read(reference, outgoing.keySet(), now).thenApply(receipts -> Optional.of(new Activity(
                    StableDigest.sha256(latest.source().scope().clientId(), latest.source().scope().scopeId(), latest.source().sourceId()),
                    latest.occurredAt(), outgoing.entrySet().stream().filter(entry -> receipts.containsKey(entry.getKey())
                            && receipts.get(entry.getKey()).confirmed() > 0).map(Map.Entry::getValue).max(Comparator.naturalOrder()).orElse(Instant.EPOCH))));
        });
    }

    /**
     * 只读互动时刻，不保存正文。
     * @param userKey 最后来信身份
     * @param userAt 最后来信时刻
     * @param replyAt 最后已确认回复时刻
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.9.0-l
     */
    record Activity(String userKey, Instant userAt, Instant replyAt) {}

    /**
     * 原候选的固定资料与占用；不会在校验时悄悄续领新候选。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.9.0-l
     */
    private final class CareSession implements IProactiveContextSource.ISession {
        private final MemoryNamespaceReference reference;
        private final UUID user;
        private final ProactiveTargets.Target target;
        private final SocialState state;
        private final Activity activity;
        private final JsonNode config;
        private final int initial;
        /**
         * @param reference 固定空间
         * @param user 主体
         * @param target 原目标
         * @param state 占用状态
         * @param activity 原互动
         * @param config 原配置
         * @param initial 初值
         */
        private CareSession(MemoryNamespaceReference reference, UUID user, ProactiveTargets.Target target, SocialState state,
                            Activity activity, JsonNode config, int initial) {
            this.reference = reference;
            this.user = user;
            this.target = target;
            this.state = state;
            this.activity = activity;
            this.config = config.deepCopy();
            this.initial = initial;
        }
        /** @return 当前人格可用的关怀缘由，不捏造用户遭遇 */
        @Override public String context() {
            int hours = (int) Duration.between(state.lastUser(), Instant.now()).toHours();
            int decay = access.settings(target.scope(), "mood").map(value -> value.path("decayHours").asInt(12)).orElse(12);
            String feeling = state.reminders() == 0 ? "想念或惦记" : "有些担心、失落或轻微闹别扭，具体取决于原人格";
            return "主动关怀参考：当前是与该用户的私聊，亲近度=" + state.affinity() + "，距上次来信约 " + hours
                    + " 小时；此前未回复的关怀=" + state.reminders() + " 次。可表现为" + feeling
                    + "；心情倾向=" + state.currentMood(Instant.now(), decay) + "。"
                    + "已发言未回复=" + activity.replyAt().isAfter(state.lastUser())
                    + "。结合原人格决定弃言或简短关心，不把离线当危险证据，不要求立刻回复，不施加愧疚，也不要重复之前的问候。";
        }
        /**
         * @param control 当前控制
         * @return 绑定、组件、来信及占用都未改变时完成
         */
        @Override public CompletionStage<Void> verify(ExecutionControl control) {
            if (!access.readable(target.scope()) || access.settings(target.scope(), "care").filter(config::equals).isEmpty()
                    || access.settings(target.scope(), "affinity").isEmpty()
                    || transport.user(target.scope(), target.id()).filter(user::equals).isEmpty()) {
                return CompletableFuture.failedStage(new IllegalStateException("主动关怀配置或真实用户映射已变化。"));
            }
            return control.guard(references.verify(reference)).thenCompose(ignored -> states.read(reference, user, initial))
                    .thenCompose(current -> {
                        if (!current.pending().equals(state.pending()) || !current.sourceKey().equals(state.sourceKey())
                                || current.affinity() < config.path("minimumAffinity").asInt(70)) {
                            throw new IllegalStateException("用户已再次联系或关怀占用发生变化，本候选停止。");
                        }
                        return CareContextSource.this.activity(reference, user, Instant.now());
                    }).thenAccept(current -> {
                        if (current.isEmpty() || !current.get().equals(activity)) {
                            throw new IllegalStateException("关怀生成期间已有新来信或新回复，本候选停止。");
                        }
                    });
        }
        /**
         * @param result 实际发送结果
         * @return 真实确认才计数；未知结果保留占用直到新来信
         */
        @Override public CompletionStage<Void> finish(Delivery result) {
            if (result == Delivery.FAILED) { return CompletableFuture.completedStage(null); }
            var now = Instant.now();
            return states.update(reference, user, initial, "", now, old -> {
                if (!old.pending().equals(state.pending())) { return old; }
                var mood = access.settings(target.scope(), "mood");
                boolean disappointed = result == Delivery.SENT && old.reminders() > 0 && mood.isPresent();
                int feeling = disappointed ? Math.clamp(old.currentMood(now, mood.get().path("decayHours").asInt(12)) - 10, -100, 100) : old.mood();
                return new SocialState(old.affinity(), feeling, disappointed ? now : old.moodAt(), old.appraisedAt(), old.evidenceHash(),
                        old.lastUser(), old.sourceKey(), result == Delivery.SENT ? now : old.lastCare(),
                        old.reminders() + (result == Delivery.SENT ? 1 : 0), "", old.nextCare(), old.revision() + 1);
            }).thenApply(ignored -> null);
        }
    }
}
