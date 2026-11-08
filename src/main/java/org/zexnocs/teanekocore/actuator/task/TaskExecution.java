package org.zexnocs.teanekocore.actuator.task;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.function.IVoidCallable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

/**
 * 单个任务的协作停止和物理占用；等待终止不等于 Callable 或外部工作结束。
 * <br>2026/11/07：事务边界延迟投递线程中断，避免回滚时破坏 JDBC 连接。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
public final class TaskExecution {
    private static final ScopedValue<Attempt> CURRENT = ScopedValue.newInstance();
    private final ExecutionControl control = ExecutionControl.create();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final Set<Attempt> attempts = new HashSet<>();
    private boolean stopped;
    private boolean settled;
    private boolean external;

    /**
     * 创建任务独有控制，不创建线程或执行用户代码。
     */
    public TaskExecution() {
        control.onTermination(ignored -> stopAttempts());
    }

    /**
     * @return 借用的任务控制，阶段和实际副作用前须复核
     */
    public ExecutionControl control() {
        return control;
    }

    /**
     * @return 所有实际尝试及已声明外部工作收尾后才结束的只读阶段
     */
    public CompletionStage<Void> completion() {
        return completion.minimalCompletionStage();
    }

    /**
     * @return 本任务是否已撤销后续执行资格
     */
    public synchronized boolean stopped() {
        return stopped;
    }

    /**
     * @return 在排队前登记的单次尝试，重试同样受原任务容量约束
     */
    public synchronized Attempt prepare() {
        control.checkActive();
        var attempt = new Attempt();
        attempts.add(attempt);
        return attempt;
    }

    /**
     * Callable 返回空值，保留外部结果占用，未知结果不能通过超时自动重发。
     */
    public synchronized void waitingExternal() {
        if (!settled) {
            external = true;
        }
    }

    /**
     * 真实同步结果或外部完成已观察到，等所有实际尝试退出后再归还容量。
     */
    public synchronized void settled() {
        settled = true;
        external = false;
        finishIfReady();
    }

    /**
     * 请求协作停止；运行中线程会被中断，未合作代码仍保留物理占用。
     */
    public void stop() {
        control.cancel();
    }

    /**
     * 在任务拥有的调用栈中复核；普通非任务调用不受影响。
     */
    public static void checkCurrent() {
        if (CURRENT.isBound()) {
            CURRENT.get().checkActive();
        }
    }

    /**
     * 2026/11/07：在完整资源收尾边界内只撤销控制资格，离开边界后再投递本任务的线程中断。
     * 此方法不隐藏取消，也不允许调用方跳过提交前的 {@link #checkCurrent()}。
     *
     * @param action 包含提交或回滚的同步工作，不得把资源收尾转交给未等待的异步任务
     * @throws Exception 原工作或执行控制失败时
     */
    public static void withDeferredInterrupt(IVoidCallable action) throws Exception {
        if (CURRENT.isBound()) {
            CURRENT.get().withDeferredInterrupt(action);
        } else {
            action.call();
        }
    }

    /**
     * 在控制器通知线程撤销排队工作并中断仍在运行的尝试。
     */
    private synchronized void stopAttempts() {
        stopped = true;
        for (var attempt : Set.copyOf(attempts)) {
            attempt.stop();
        }
        finishIfReady();
    }

    /**
     * 只能在持有本任务锁时完成；外部未知工作不得凭等待超时释放。
     */
    private void finishIfReady() {
        if ((settled || stopped) && !external && attempts.isEmpty()) {
            completion.complete(null);
        }
    }

    /**
     * 单次调度的原子开始和收尾，取消排队与执行线程竞争同一任务锁。
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    public final class Attempt {
        private @Nullable Future<?> scheduled;
        private @Nullable Thread worker;
        private boolean ended;
        // 2026/11/07：每次尝试分别计数，正常重试与嵌套资源边界不能互相恢复中断。
        private int interruptDeferrals;

        /**
         * 仅由所属任务登记，不允许外部创建未计数的尝试。
         */
        private Attempt() {
        }

        /**
         * @param future 可撤销的排队句柄，运行阶段仍由本尝试追踪
         */
        public void scheduled(Future<?> future) {
            synchronized (TaskExecution.this) {
                scheduled = future;
                if (ended) {
                    future.cancel(false);
                }
            }
        }

        /**
         * @param action 原任务同步阶段链
         * @return 真正开始过原任务时为真
         */
        public boolean run(Runnable action) {
            synchronized (TaskExecution.this) {
                if (ended) {
                    return false;
                }
                worker = Thread.currentThread();
            }
            try {
                ScopedValue.where(CURRENT, this).run(action);
            } finally {
                synchronized (TaskExecution.this) {
                    worker = null;
                    ended = true;
                    attempts.remove(this);
                    finishIfReady();
                }
            }
            return true;
        }

        /**
         * 调度失败时并没有进入工作，撤销此排队占用。
         */
        public void rejected() {
            synchronized (TaskExecution.this) {
                ended = true;
                attempts.remove(this);
                finishIfReady();
            }
        }

        /** 在所属任务的调用栈内复核同一控制，不改变线程中断状态。 */
        private void checkActive() {
            control.checkActive();
        }

        /**
         * 在任务锁内串行化保护边界和停止通知，实际资源工作不占有任务锁。
         *
         * @param action 包含完整资源收尾的工作
         * @throws Exception 取消或原工作失败时
         */
        private void withDeferredInterrupt(IVoidCallable action) throws Exception {
            synchronized (TaskExecution.this) {
                control.checkActive();
                interruptDeferrals++;
            }
            try {
                action.call();
            } finally {
                synchronized (TaskExecution.this) {
                    interruptDeferrals--;
                    if (interruptDeferrals == 0 && stopped) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        /**
         * 在任务锁内区分尚未运行与实际运行，后者必须等待线程退出。
         */
        private void stop() {
            if (worker != null) {
                if (interruptDeferrals == 0) {
                    worker.interrupt();
                }
            } else {
                ended = true;
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
                attempts.remove(this);
            }
        }
    }
}
