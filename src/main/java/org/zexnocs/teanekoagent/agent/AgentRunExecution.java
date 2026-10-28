package org.zexnocs.teanekoagent.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonValue;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * 尚未启动或正在执行的独立轮次句柄，运行引用不进入组件或 JSON。
 * 拥有者先登记取消/观察关系，再显式启动；不能通过取消结果视图停止根执行。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@JsonIgnoreType
public final class AgentRunExecution {
    private final CompletionStage<AgentRunResult> result;
    private final AtomicReference<Runnable> starter;
    private final BooleanSupplier cancellation;
    private final Predicate<ExecutionControl> ownership;

    /**
     * 固定显式启动、取消及链路端口；实现必须保证未 start 时不执行模型或工具。
     * @param result 真实结束信号，取消也必须完成
     * @param starter 至多执行一次的非阻塞启动操作
     * @param cancellation 只影响本轮的取消操作
     * @param ownership 判断显式控制是否属于本轮，不进行 I/O 或读取权限
     * @throws NullPointerException 当完成信号或运行端口为空时
     */
    public AgentRunExecution(CompletionStage<AgentRunResult> result, Runnable starter,
            BooleanSupplier cancellation, Predicate<ExecutionControl> ownership) {
        this.result = Objects.requireNonNull(result, "轮次完成信号不能为空。").toCompletableFuture().minimalCompletionStage();
        this.starter = new AtomicReference<>(Objects.requireNonNull(starter, "轮次启动操作不能为空。"));
        this.cancellation = Objects.requireNonNull(cancellation, "轮次取消操作不能为空。");
        this.ownership = Objects.requireNonNull(ownership, "轮次执行链检查不能为空。");
        this.result.whenComplete((value, failure) -> this.starter.set(null));
    }

    /**
     * 至多启动一次，取消已经生效时实现的启动操作须保持无业务动作。
     * @return 与 result 相同的只读完成阶段
     */
    public CompletionStage<AgentRunResult> start() {
        var action = starter.getAndSet(null);
        if (action != null && !result.toCompletableFuture().isDone()) { action.run(); }
        return result;
    }

    /**
     * 观察实际结束，不自动启动业务。
     * @return 只读结果阶段
     */
    public CompletionStage<AgentRunResult> result() { return result; }

    /**
     * 请求取消并释放未启动的启动回调引用；不回滚已有副作用。
     * @return 本次是否首次请求取消
     */
    public boolean cancel() {
        starter.set(null);
        return !result.toCompletableFuture().isDone() && cancellation.getAsBoolean();
    }

    /**
     * 判断控制是否源自本轮，供上层防止显式递归等待同一邮箱。
     * @param control 宿主显式传入的控制
     * @return 当前轮次拥有该调用链时为 true
     * @throws NullPointerException 当控制为空时
     */
    public boolean contains(ExecutionControl control) {
        return ownership.test(Objects.requireNonNull(control, "执行链控制不能为空。"));
    }

    /**
     * 提供不展开运行回调或身份的固定摘要。
     * @return 固定运行句柄说明
     */
    @Override @JsonValue public String toString() { return "AgentRunExecution[运行句柄]"; }
}
