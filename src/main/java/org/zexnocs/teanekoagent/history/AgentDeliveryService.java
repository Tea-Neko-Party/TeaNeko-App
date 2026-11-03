package org.zexnocs.teanekoagent.history;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 原发送链的持久回执边界，先占用再调用宿主守卫和传输，失败保守记录不确定且不补发。
 * <br>2026/11/03：支持类型扩展提供的固定历史投影与实际内容摘要。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/03：在实际逐段发送边界记录平台回执，不把模型成功当作交付。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.7.0-l
 */
public final class AgentDeliveryService {
    private org.zexnocs.teanekoagent.agent.monitor.@Nullable AgentActivityTimeline timeline;

    /**
     * 2026/11/03：注入只观察脱敏节点的宿主窗口。
     * @param timeline 实际活动窗口
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installTimeline(org.zexnocs.teanekoagent.agent.monitor.AgentActivityTimeline timeline) {
        this.timeline = timeline;
    }
    private final IAgentDeliveryStore store;
    private final Executor executor;
    private final Clock clock;
    private final Supplier<Duration> retention;
    /**
     * 保存借用端口，构造不执行 I/O。
     * @param store 原发送账本
     * @param executor 业务域
     * @param clock 当前时间
     * @param retention 回执保留期
     */
    public AgentDeliveryService(IAgentDeliveryStore store, Executor executor, Clock clock, Supplier<Duration> retention) {
        this.store = store; this.executor = executor; this.clock = clock; this.retention = retention;
    }
    /**
     * 每段等待真实平台完成及回执提交，原授权检查必须包含在实际发送回调内。
     * @param reference 固定原空间；显式无记忆宿主为空时不自动添加存储
     * @param actor 原身份
     * @param requestId 原请求
     * @param index 原段号
     * @param total 总段数
     * @param part 已验证发送内容
     * @param guardedSend 复核当前授权和原目标后执行的原传输
     * @return 实际发送及回执完成阶段
     */
    public CompletionStage<Void> send(@Nullable MemoryNamespaceReference reference, AgentActorContext actor, UUID requestId, int index, int total,
            AgentReplyPlan.Part part, Supplier<CompletionStage<Void>> guardedSend) {
        // 2026/11/03：旧图文入口继续保留原摘要语义。
        String text = part instanceof AgentReplyPlan.Text words ? words.value() : "[已确认发送图片]";
        String content = part instanceof AgentReplyPlan.Image(String url) ? url : text;
        return sendRecorded(reference, actor, requestId, index, total, text,
                StableDigest.sha256(part.getClass().getSimpleName(), part.mode().name(), content), guardedSend);
    }
    /**
     * 2026/11/03：类型扩展提交有界投影，不把图片路径或原始数据写入历史。
     * @param reference 原空间
     * @param actor 原身份
     * @param requestId 原请求
     * @param index 当前段号
     * @param total 总段数
     * @param text 固定可读投影
     * @param digest 完整实际内容摘要
     * @param guardedSend 真正发送前的原守卫和传输
     * @return 实际平台回执及账本提交
     */
    public CompletionStage<Void> sendRecorded(@Nullable MemoryNamespaceReference reference, AgentActorContext actor, UUID requestId,
            int index, int total, String text, String digest, Supplier<CompletionStage<Void>> guardedSend) {
        return CompletableFuture.supplyAsync(() -> {
            // 2026/11/03：无持久化模式也只能按实际平台阶段报告完成。
            if (reference == null) { return observed(actor, requestId, index, guardedSend); }
            var duration = retention.get();
            if (duration.compareTo(Duration.ofDays(1)) < 0 || duration.compareTo(Duration.ofDays(365)) > 0) { throw new IllegalArgumentException("平台回执保留期必须在一至三百六十五天内。"); }
            String projection = text.length() > 4096 ? text.substring(0, 4000) + "（发送正文较长，尾部省略）" : text;
            var now = clock.instant(); var command = new IAgentDeliveryStore.Part(reference, actor, requestId, index, total,
                    digest, projection, now, now.plus(duration));
            return store.begin(command).thenComposeAsync(acquired -> {
                if (!acquired) { return CompletableFuture.completedStage(null); }
                CompletionStage<Void> sending;
                try { sending = observed(actor, requestId, index, guardedSend); } catch (RuntimeException failure) { sending = CompletableFuture.failedStage(failure); }
                return sending.thenComposeAsync(ignored -> store.finish(command, true, clock.instant()), executor)
                        .exceptionallyCompose(failure -> store.finish(command, false, clock.instant()).handle((ignored, recordFailure) -> {
                            // 已发送但回执写入失败也不补发，不把数据库失败解释为平台未收到。
                            throw new CompletionException(failure);
                        }));
            }, executor);
        }, executor).thenCompose(stage -> stage);
    }

    /**
     * 2026/11/03：记录实际调用及真实完成，不记录消息正文或把失败判为未送达。
     * @param actor 原目标
     * @param request 原请求
     * @param index 当前段
     * @param send 原守卫和发送
     * @return 原平台完成阶段
     */
    private CompletionStage<Void> observed(AgentActorContext actor, UUID request, int index,
                                            Supplier<CompletionStage<Void>> send) {
        var observer = timeline;
        if (observer == null) {
            return send.get();
        }
        observer.delivery(request, actor.scope(), index, "STARTED");
        try {
            return send.get().whenComplete((ignored, failure) -> observer.delivery(request, actor.scope(), index,
                    failure == null ? "SENT" : "INDETERMINATE"));
        } catch (RuntimeException failure) {
            observer.delivery(request, actor.scope(), index, "INDETERMINATE");
            throw failure;
        }
    }
}
