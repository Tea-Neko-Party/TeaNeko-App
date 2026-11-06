package org.zexnocs.teanekoagent.component.proactive;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.proactive.adapter.ProactiveTargets;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor.Delivery;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * 组合由容器一次性装配的主动上下文来源，高频路径不执行发现扫描。
 * <br>2026/11/06：组件只收紧候选资格，空集合保留原主动行为。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Service
public final class ProactiveContexts {
    private final List<IProactiveContextSource> sources;
    /** @param sources 容器已发布的扩展列表 */
    public ProactiveContexts(List<IProactiveContextSource> sources) { this.sources = List.copyOf(sources); }

    /**
     * @param target 固定目标
     * @param kind 发生类型
     * @param reference 可选空间
     * @param request 原请求
     * @param control 当前控制
     * @return 完整候选组合；未装配来源时保持原行为
     */
    public CompletionStage<Selection> prepare(ProactiveTargets.Target target, AgentTriggerGate.Kind kind,
            @Nullable MemoryNamespaceReference reference, UUID request, ExecutionControl control) {
        CompletionStage<Selection> stage = CompletableFuture.completedStage(new Selection(true, List.of()));
        for (var source : sources) {
            // 2026/11/06：资格读取和异步准备均可能失败，均由已准备会话负责收尾。
            stage = stage.thenCompose(previous -> CompletableFuture.completedStage(null).thenCompose(ignored -> {
                if (!previous.allowed() || !source.applies(target, kind)) {
                    return CompletableFuture.completedStage(previous);
                }
                if (reference == null) { return CompletableFuture.completedStage(new Selection(false, previous.sessions())); }
                return control.guard(source.prepare(target, reference, request, control)).thenApply(value -> {
                    var all = new ArrayList<>(previous.sessions());
                    value.ifPresent(all::add);
                    return new Selection(value.isPresent(), List.copyOf(all));
                });
            }).exceptionallyCompose(failure -> previous.finish(Delivery.ABSTAINED).handle((ignored, cleanup) -> {
                if (cleanup != null) { failure.addSuppressed(cleanup); }
                throw new CompletionException(failure);
            })));
        }
        return stage;
    }

    /**
     * 一次候选的扩展资格与实际会话。
     * @param allowed 各已装配来源均同意
     * @param sessions 已准备的来源
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.8.6-l
     */
    public record Selection(boolean allowed, List<IProactiveContextSource.Session> sessions) {
        /** @return 模型可读的候选资料 */
        public String text() { return String.join("\n", sessions.stream().map(IProactiveContextSource.Session::context).toList()); }
        /**
         * @param control 借用控制
         * @return 所有来源复核完成
         */
        public CompletionStage<Void> verify(ExecutionControl control) {
            CompletionStage<Void> stage = CompletableFuture.completedStage(null);
            for (var session : sessions) { stage = stage.thenCompose(ignored -> session.verify(control)); }
            return stage;
        }
        /**
         * @param result 实际结果
         * @return 所有来源结果提交
         */
        public CompletionStage<Void> finish(Delivery result) {
            // 2026/11/06：某个扩展提交失败不能阻止其他扩展释放占用，最终仍传播完整失败。
            CompletionStage<@Nullable Throwable> stage = CompletableFuture.completedStage(null);
            for (var session : sessions) {
                stage = stage.thenCompose(previous -> CompletableFuture.completedStage(null)
                        .thenCompose(ignored -> session.finish(result)).handle((ignored, failure) -> {
                            if (failure == null) { return previous; }
                            if (previous == null) { return failure; }
                            previous.addSuppressed(failure);
                            return previous;
                        }));
            }
            return stage.thenAccept(failure -> {
                if (failure != null) { throw new CompletionException(failure); }
            });
        }
    }
}
