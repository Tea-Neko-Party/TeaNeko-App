package org.zexnocs.teanekocore.api_response;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.api_response.exception.APIRequestTimeoutException;
import reactor.core.Disposables;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 单次订阅的完成/终止仲裁。结果、定时器、监听与 HTTP 订阅共用一个生命周期。
 *
 * @param <T> 组件值类型
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class APIRequestExecution<T> {
    private final ExecutionPolicy policy;
    private final Scheduler timer;
    private final CompletableFuture<T> future;
    private final reactor.core.Disposable.Composite resources = Disposables.composite();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final long startedAt;
    private final long timeoutNanos;

    /**
     * 登记请求完成、控制终止和阶段计时的资源回调；结束时统一释放，借用的调度器不归请求所有。
     *
     * @param policy 本阶段的时限与借用控制策略
     * @param timeout 阶段时限；按接口约定可为空
     * @param timer 阶段计时使用的借用调度器
     * @param future 承载最终结果的根任务
     * @param managedTimeout 是否将排队及重试等待纳入同一阶段时限
     */
    APIRequestExecution(ExecutionPolicy policy, Duration timeout, Scheduler timer,
                        CompletableFuture<T> future, boolean managedTimeout) {
        this.policy = policy;
        this.timer = timer;
        this.future = future;
        this.startedAt = timer.now(TimeUnit.NANOSECONDS);
        this.timeoutNanos = managedTimeout ? nanos(timeout) : -1;
        future.whenComplete((_, _) -> {
            if (completed.compareAndSet(false, true)) {
                disposeResources(); // finish().cancel(...) 或手动完成也释放订阅。
            }
        });
        if (policy.control() != null) {
            var registration = policy.control().onTermination(reason -> fail(new ExecutionStoppedException(reason)));
            resources.add(registration::close);
        }
        if (managedTimeout && !isDone()) {
            try {
                resources.add(timer.schedule(() -> fail(new APIRequestTimeoutException()),
                        timeoutNanos, TimeUnit.NANOSECONDS));
            } catch (RuntimeException failure) {
                fail(failure);
            }
        }
    }

    /**
     * 检查本请求是否已选定终止结果，防止继续订阅或重复完成。
     *
     * @return 本请求是否已接受成功、失败、取消或超时结果
     */
    boolean isDone() {
        return completed.get();
    }

    /**
     * 先登记可取消订阅再在指定调度器启动传输，排队期间也受取消和阶段时限约束。
     *
     * @param source 待处理的来源数据
     * @param scheduler 由装配方管理的调度器
     * @param onAccepted 仅在响应被接受后执行的处理回调
     */
    void subscribe(Mono<T> source, Scheduler scheduler, Consumer<T> onAccepted) {
        var subscriber = new BaseSubscriber<T>() {
            /**
             * 将实际传输值交给请求成功仲裁，只有被接受的响应才推进缓存与完成链。
             *
             * @param value 待处理的值
             */
            @Override
            protected void hookOnNext(@NonNull T value) {
                succeed(value, onAccepted);
            }

            /**
             * 将传输失败交给统一终止路径，释放资源并结束等待。
             *
             * @param failure 需要传播或分类的异常
             */
            @Override
            protected void hookOnError(@NonNull Throwable failure) {
                fail(failure);
            }

            /**
             * 处理无正文结束的传输；尚无结果时明确失败，避免 Future 永久悬挂。
             */
            @Override
            protected void hookOnComplete() {
                if (!isDone()) {
                    fail(new IllegalStateException("API response body is empty."));
                }
            }
        };
        // 先登记可取消句柄，再订阅；同步完成或取消发生在 onSubscribe 之前也不会遗漏资源。
        resources.add(subscriber);
        if (!isDone()) {
            try {
                Mono.defer(() -> {
                    checkActive();
                    return isDone() ? Mono.empty() : source;
                }).subscribeOn(scheduler).subscribe(subscriber);
            } catch (RuntimeException failure) {
                fail(failure);
            }
        }
    }

    /**
     * 仲裁响应与停止状态；仅接受一次有效响应，在控制锁外释放资源、更新缓存并完成结果。
     *
     * @param value 待处理的值
     * @param onAccepted 仅在响应被接受后执行的处理回调
     */
    void succeed(T value, Consumer<T> onAccepted) {
        try {
            checkActive();
            boolean accepted = policy.control() == null
                    ? completed.compareAndSet(false, true)
                    : policy.control().whileActive(() -> completed.compareAndSet(false, true));
            if (accepted) {
                var cleanupFailure = disposeResources();
                if (cleanupFailure != null) {
                    future.completeExceptionally(cleanupFailure);
                    return;
                }
                // Future 回调和缓存操作不在 ExecutionControl 生命周期锁内执行。
                try {
                    onAccepted.accept(value);
                    future.complete(value);
                } catch (RuntimeException failure) {
                    future.completeExceptionally(failure);
                }
            }
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    /**
     * 保留已生效的取消或超时原因，释放全部请求资源后以异常结束等待。
     *
     * @param failure 需要传播或分类的异常
     */
    void fail(Throwable failure) {
        try {
            checkActive();
        } catch (RuntimeException stopped) {
            failure = stopped;
        }
        if (completed.compareAndSet(false, true)) {
            var cleanupFailure = disposeResources();
            if (cleanupFailure != null && cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
            future.completeExceptionally(failure);
        }
    }

    /**
     * 同时检查整轮控制和单次阶段时限，拒绝已经过期的响应或传输启动。
     *
     * @throws APIRequestTimeoutException 当本次 HTTP 阶段时限已经耗尽时
     */
    private void checkActive() {
        policy.checkActive();
        if (timeoutNanos >= 0 && timer.now(TimeUnit.NANOSECONDS) - startedAt >= timeoutNanos) {
            throw new APIRequestTimeoutException();
        }
    }

    /**
     * 将阶段时限转为调度器使用的纳秒数，超出 long 范围时采用最大可表示值。
     *
     * @param duration 需要换算或推进的时长
     * @return 可用于调度的正纳秒数，超出范围时为 Long.MAX_VALUE
     */
    private static long nanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 释放计时器、订阅和终止监听；将清理异常交给结果仲裁方，避免等待悬挂。
     *
     * @return 资源清理失败时的异常；全部清理成功时为 null
     */
    private RuntimeException disposeResources() {
        try {
            resources.dispose();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

}
