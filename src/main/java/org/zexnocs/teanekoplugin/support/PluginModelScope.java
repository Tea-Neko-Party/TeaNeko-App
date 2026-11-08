package org.zexnocs.teanekoplugin.support;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 模型辅助服务拥有的启用代次，取消自有子控制后等待原业务链结束再释放句柄。
 * <br>2026/11/07：共享三个模型业务的真实排空合同，不关闭调用方控制或宿主线程池。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class PluginModelScope {
    private final Map<ExecutionControl, CompletableFuture<Void>> running = new HashMap<>();
    private boolean active;
    private CompletableFuture<Void> stopping = CompletableFuture.completedFuture(null);

    /**
     * 开放一个新的启用代次，尚未排空的旧代次不能被复用。
     * @throws IllegalStateException 当上一代仍在停止时
     */
    public synchronized void start() {
        if (!stopping.isDone()) {
            throw new IllegalStateException("模型辅助服务仍在停止，不能提前重新启用。");
        }
        active = true;
    }

    /** @return 当前是否允许创建新的模型工作。 */
    public synchronized boolean active() {
        return active;
    }

    /**
     * 在锁内登记自有子控制，在锁外执行真实业务；停止只取消子控制。
     * @param parent 调用方借用控制
     * @param action 返回真实完成阶段的业务，不能传入提前取消观察的包装阶段
     * @param <T> 结果类型
     * @return 原业务完成后通知的只读阶段，调用方取消观察不影响内部排空
     */
    public <T> CompletionStage<T> execute(ExecutionControl parent,
            Function<ExecutionControl, CompletionStage<T>> action) {
        final ExecutionControl control;
        var completion = new CompletableFuture<Void>();
        synchronized (this) {
            if (!active) {
                return CompletableFuture.failedStage(new IllegalStateException("模型辅助服务未启用。"));
            }
            control = parent.child();
            running.put(control, completion);
        }
        CompletionStage<T> work;
        try {
            control.checkActive();
            work = Objects.requireNonNull(action.apply(control), "模型业务必须返回真实完成阶段。");
        } catch (RuntimeException failure) {
            work = CompletableFuture.failedStage(failure);
        }
        var observed = new CompletableFuture<T>();
        // 2026/11/07：调用者只能取消自己的观察，内部清理始终等待真实阶段。
        work.whenComplete((result, failure) -> {
            control.close();
            synchronized (this) {
                running.remove(control);
            }
            completion.complete(null);
            if (failure == null) {
                observed.complete(result);
            } else {
                observed.completeExceptionally(failure);
            }
        });
        return observed.minimalCompletionStage();
    }

    /**
     * 关闭入口、取消子工作，并在全部原阶段结束后执行资源释放。
     * @param release 真实结束后释放自有句柄的动作
     * @return 完整排空与释放阶段，重复停止复用同一结果
     */
    public CompletionStage<Void> stop(Runnable release) {
        final List<ExecutionControl> controls;
        final CompletableFuture<Void> drain;
        final CompletableFuture<Void> result;
        synchronized (this) {
            if (!active) {
                return stopping.minimalCompletionStage();
            }
            active = false;
            controls = List.copyOf(running.keySet());
            drain = CompletableFuture.allOf(running.values().toArray(CompletableFuture<?>[]::new));
            result = new CompletableFuture<>();
            stopping = result;
        }
        controls.forEach(ExecutionControl::cancel);
        drain.whenComplete((ignored, failure) -> {
            try {
                release.run();
                result.complete(null);
            } catch (RuntimeException cleanupFailure) {
                result.completeExceptionally(cleanupFailure);
            }
        });
        return result.minimalCompletionStage();
    }
}
