package org.zexnocs.teanekocore.plugin.operation;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 不可变异步环绕链，借用原控制及插件目录，不提交第二套调度或自动重试。
 * @param <I> 输入类型
 * @param <O> 输出类型
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public final class OperationPipeline<I, O> {
    private final PluginRegistry plugins;
    private final List<Binding<I, O>> bindings;
    private final boolean shortCircuit;
    /**
     * 固定顺序，高 order 先进入，重名拒绝。
     * @param plugins 插件目录
     * @param bindings 环绕节点
     * @param shortCircuit 是否允许不执行下一节点
     * @throws IllegalArgumentException 当节点重名时
     */
    public OperationPipeline(PluginRegistry plugins, List<Binding<I, O>> bindings, boolean shortCircuit) {
        this.plugins = Objects.requireNonNull(plugins); this.shortCircuit = shortCircuit;
        var ids = new HashSet<String>();
        for (var binding : bindings) {
            plugins.requireRegistered(binding.pluginId());
            if (!ids.add(binding.pluginId() + ":" + binding.id())) { throw new IllegalArgumentException("异步拦截器标识重复。"); }
        }
        this.bindings = bindings.stream().sorted(Comparator.<Binding<I, O>>comparingInt(Binding::order).reversed()
                .thenComparing(Binding::pluginId).thenComparing(Binding::id)).toList();
    }
    /**
     * 为每次调用捕获独立版本，禁用节点不参与新调用，迟到结果不能通过。
     * @param input 业务输入
     * @param control 借用执行控制
     * @param terminal 宿主实际操作
     * @return 完整环绕及终端结果
     */
    public CompletionStage<O> execute(I input, ExecutionControl control, AsyncOperation<I, O> terminal) {
        try {
            control.checkActive();
            var selected = bindings.stream().filter(binding -> plugins.isEnabled(binding.pluginId())).toList();
            var tokens = selected.stream().map(binding -> plugins.capture(binding.pluginId())).toList();
            return control.guard(invoke(0, selected, input, control, terminal)).thenApply(value -> {
                control.checkActive(); tokens.forEach(plugins::check); return value;
            });
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 递归组合不可变节点，拒绝下一节点重复或完成后才调用。
     * @param index 当前节点
     * @param selected 固定节点
     * @param input 当前输入
     * @param control 借用控制
     * @param terminal 宿主操作
     * @return 当前子链结果
     */
    private CompletionStage<O> invoke(int index, List<Binding<I, O>> selected, I input,
            ExecutionControl control, AsyncOperation<I, O> terminal) {
        try {
            control.checkActive();
            if (index == selected.size()) { return Objects.requireNonNull(terminal.execute(input), "实际操作不能返回空阶段。"); }
            return around(input, shortCircuit, selected.get(index).interceptor(), value -> invoke(index + 1, selected, value, control, terminal));
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 为单个环绕提供一次性下一节点，并等待已经启动的下游；捕获重复调用也不能伪装成功。
     * 下游普通失败可由拦截器明确恢复，取消及插件版本仍由外层守卫检查。
     * @param input 原输入
     * @param shortCircuit 是否允许跳过
     * @param interceptor 环绕实现
     * @param next 实际下一节点
     * @param <I> 输入类型
     * @param <O> 输出类型
     * @return 完整节点阶段
     */
    public static <I, O> CompletionStage<O> around(I input, boolean shortCircuit, AsyncInterceptor<I, O> interceptor, AsyncOperation<I, O> next) {
        var lock = new Object(); var called = new AtomicBoolean(); var completed = new AtomicBoolean(); var violation = new AtomicBoolean();
        var downstream = new CompletableFuture<O>();
        CompletionStage<O> pending;
        try {
            pending = Objects.requireNonNull(interceptor.intercept(input, value -> {
                synchronized (lock) {
                    if (completed.get() || !called.compareAndSet(false, true)) {
                        violation.set(true); return CompletableFuture.failedStage(new IllegalStateException("下一节点不可重复或迟到调用。"));
                    }
                }
                try {
                    Objects.requireNonNull(next.execute(value), "下一节点不能返回空阶段。").whenComplete((result, failure) -> {
                        if (failure == null) { downstream.complete(result); } else { downstream.completeExceptionally(failure); }
                    });
                } catch (RuntimeException failure) { downstream.completeExceptionally(failure); }
                return downstream.minimalCompletionStage();
            }), "拦截器不能返回空阶段。");
        } catch (RuntimeException failure) {
            pending = CompletableFuture.failedStage(failure);
        }
        return pending.handle((value, failure) -> {
            synchronized (lock) { completed.set(true); }
            CompletionStage<Void> drained = called.get() ? downstream.handle((ignored, childFailure) -> null) : CompletableFuture.completedStage(null);
            return drained.thenApply(ignored -> {
                if (failure != null) { throw new CompletionException(failure); }
                if (violation.get() || !shortCircuit && !called.get()) { throw new IllegalStateException("异步拦截违反下一节点执行约定。"); }
                return value;
            });
        }).thenCompose(stage -> stage);
    }
    /**
     * 插件所属的有序类型化节点。
     * @param pluginId 所属插件
     * @param id 插件内局部标识
     * @param order 进入顺序，越大越先
     * @param interceptor 环绕实现
     * @param <I> 输入类型
     * @param <O> 输出类型
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.0-l
     */
    public record Binding<I, O>(String pluginId, String id, int order, AsyncInterceptor<I, O> interceptor) {
        /**
         * 验证节点身份及实现。
         * @param pluginId 所属插件
         * @param id 局部标识
         * @param order 进入顺序
         * @param interceptor 实际实现
         */
        public Binding {
            Objects.requireNonNull(pluginId); Objects.requireNonNull(interceptor);
            if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,63}")) { throw new IllegalArgumentException("拦截器标识无效。"); }
        }
    }
}
