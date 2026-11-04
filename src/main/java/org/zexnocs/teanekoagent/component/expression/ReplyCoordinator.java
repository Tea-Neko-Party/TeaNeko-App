package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionDelays;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 按原 client/scope/机器人账号串行安排轮次和发送，独立拥有等待控制。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
public final class ReplyCoordinator implements AutoCloseable {
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final Supplier<ExpressionSettings> settings;
    private final Map<BotScope,CompletableFuture<?>> tails = new HashMap<>();
    private final Map<UUID,Pending<?>> entries = new HashMap<>();
    private boolean closed;
    /**
     * 借用线程设施，构造无 I/O。
     * @param executor 业务域
     * @param scheduler 计时器
     * @param clock 时间
     * @param settings 当前限制
     */
    public ReplyCoordinator(Executor executor, ScheduledExecutorService scheduler, Clock clock, Supplier<ExpressionSettings> settings) {
        this.executor=executor; this.scheduler=scheduler; this.clock=clock; this.settings=settings;
    }
    /**
     * 从生成前占用顺序位置，物理顺序等待实际工作完成。
     * @param owner 入口拥有者
     * @param actor 原身份
     * @param requestId 请求
     * @param self 机器人账号
     * @param action 完整生成与交付
     * @param <T> 结果类型
     * @return 可停止的观察阶段
     */
    public <T> CompletionStage<T> execute(String owner, AgentActorContext actor, UUID requestId, String self, Function<ExecutionControl,CompletionStage<T>> action) {
        var limits=settings.get(); var key=new BotScope(actor.scope(),self); final Pending<T> pending; final CompletableFuture<?> before;
        synchronized(this) {
            if(closed || entries.size()>=256 || entries.containsKey(requestId) || entries.values().stream().filter(p->p.key.equals(key)).count()>=limits.maxQueued()) {
                return CompletableFuture.failedStage(new IllegalStateException("Agent 入口已关闭、请求重复或队列已满。"));
            }
            var control=ExecutionControl.withDeadline(new ResourceBudget(Map.of()),clock.instant().plusSeconds(limits.timeoutSeconds()),clock,scheduler);
            pending=new Pending<>(owner,actor,key,control); before=tails.getOrDefault(key,CompletableFuture.completedFuture(null));
            entries.put(requestId,pending); tails.put(key,pending.actual);
        }
        var registration=pending.control.onTermination(reason->{
            if(!pending.actual.isDone()){pending.observed.completeExceptionally(new ExecutionStoppedException(reason));}
            // 已取消的队列项仍等待前项结束再跳过，防止后项越过仍在发送的消息。
        });
        pending.actual.whenComplete((result,failure)->{
            if(failure==null){pending.observed.complete(result);}else{pending.observed.completeExceptionally(failure);}
            synchronized(this){entries.remove(requestId,pending);tails.remove(key,pending.actual);}
            registration.close(); pending.control.close();
        });
        try {
            before.handle((ignored,failure)->null).thenRunAsync(()->{
                synchronized(this){if(pending.actual.isDone()){return;}pending.started=true;}
                CompletionStage<T> work;
                try{pending.control.checkActive();work=Objects.requireNonNull(action.apply(pending.control));}
                catch(RuntimeException failure){work=CompletableFuture.failedStage(failure);}
                work.whenComplete((result,failure)->{if(failure==null){pending.actual.complete(result);}else{pending.actual.completeExceptionally(failure);}});
            },executor).exceptionally(failure->{pending.actual.completeExceptionally(failure);return null;});
        }catch(RuntimeException failure){pending.actual.completeExceptionally(failure);}
        return pending.observed.minimalCompletionStage();
    }
    /**
     * 协调停止传给原模型任务，实际完成后解除监听。
     * @param control 协调控制
     * @param work 原工作
     * @param cancel 原停止入口
     * @param <T> 结果类型
     * @return 原实际阶段
     */
    public <T> CompletionStage<T> observe(ExecutionControl control, CompletionStage<T> work, Runnable cancel) {
        var registration=control.onTermination(ignored->cancel.run());work.whenComplete((value,failure)->registration.close());return work;
    }
    /**
     * 逐段等待和交付，回调只在前段真实完成后运行。
     * @param control 协调控制
     * @param plan 固定计划
     * @param sender 原守卫及回执入口
     * @return 整个交付
     */
    public CompletionStage<Void> send(ExecutionControl control, ReplyPlans.Approved plan, BiFunction<Integer,ReplyPlans.Step,CompletionStage<Void>> sender) {
        CompletionStage<Void> chain=CompletableFuture.completedStage(null);
        var confirmedAt = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
        for(int i=0;i<plan.steps().size();i++){
            final int index=i;var step=plan.steps().get(i);
            chain=chain.thenComposeAsync(ignored->{
                control.checkActive();
                long remaining = control.remainingTime().map(Duration::toNanos).orElse(Long.MAX_VALUE);
                long elapsed = Math.max(0, System.nanoTime() - confirmedAt.get());
                long wait = Math.max(0, TimeUnit.MILLISECONDS.toNanos(step.waitMillis()) - elapsed);
                return ExecutionDelays.waitFor(control,scheduler,Duration.ofNanos(Math.clamp(remaining, 0, wait)))
                        .thenComposeAsync(_ ->{control.checkActive();return sender.apply(index,step);},executor)
                        .thenRun(() -> confirmedAt.set(System.nanoTime()));
            },executor);
        }
        return chain;
    }
    /**
     * 仅真实发起者可以停止其原请求。
     * @param actor 请求者
     * @param requestId 请求
     * @return 已接收停止时为真
     */
    public boolean cancel(AgentActorContext actor, UUID requestId) {
        final Pending<?> pending;synchronized(this){pending=entries.get(requestId);}
        return pending!=null && pending.actor.scope().equals(actor.scope()) && actor.userId().isPresent() && pending.actor.userId().equals(actor.userId()) && pending.control.cancel();
    }
    /**
     * 入口销毁只停止自己拥有的工作。
     * @param owner 入口名称
     */
    public void cancelOwner(String owner){List<Pending<?>> selected;synchronized(this){selected=entries.values().stream().filter(p->p.owner.equals(owner)).toList();}selected.forEach(p->p.control.cancel());}
    /**
     * 宿主生命周期桥接只停止其原入口占用，不作为模型工具开放。
     * @param owner 已固定入口
     * @param requestId 原请求
     */
    public void cancelOwned(String owner, UUID requestId) {
        final Pending<?> pending; synchronized(this){pending=entries.get(requestId);}
        if(pending!=null && pending.owner.equals(owner)){pending.control.cancel();}
    }
    /**
     * 等待后复核拥有该控制的真实机器人账号，防止重连后改投其他账号。
     * @param control 原控制
     * @param scope 当前作用域
     * @param self 当前机器人账号
     * @throws IllegalStateException 当原目标已改变时
     */
    public void verifyTarget(ExecutionControl control, AgentScope scope, String self) {
        synchronized(this) {
            for(var entry:entries.values()) {
                if(entry.control==control && !entry.key.equals(new BotScope(scope,self))){throw new IllegalStateException("拟人回复的原机器人目标已改变。");}
            }
        }
    }
    /** 结束自有控制，不关闭共享执行器。 */
    @Override public void close(){List<Pending<?>> selected;synchronized(this){closed=true;selected=List.copyOf(entries.values());}selected.forEach(p->p.control.close());}
    /**
     * 原真实目标键。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param scope 作用域
     * @param self 机器人账号
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
private record BotScope(AgentScope scope,String self){}
    /**
     * 在途拥有关系，不进入 ECS 或数据库。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param <T> 结果类型
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
private static final class Pending<T>{
        final String owner;final AgentActorContext actor;final BotScope key;final ExecutionControl control;
        final CompletableFuture<T> actual=new CompletableFuture<>();final CompletableFuture<T> observed=new CompletableFuture<>();boolean started;
        /**
         * 固定自有资源。
         * @param owner 入口
         * @param actor 身份
         * @param key 目标
         * @param control 控制
         */
        Pending(String owner,AgentActorContext actor,BotScope key,ExecutionControl control){this.owner=owner;this.actor=actor;this.key=key;this.control=control;}
    }
}
