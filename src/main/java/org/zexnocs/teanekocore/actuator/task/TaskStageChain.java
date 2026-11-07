package org.zexnocs.teanekocore.actuator.task;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.util.List;

/**
 * 基础的任务执行阶段链。
 * 阶段链的执行应该在一个线程中，不应该横跨线程。
 * 执行顺序为 阶段A → 阶段B → ... → 阶段N → 任务 → 阶段N → ... → 阶段B → 阶段A → 提交任务
 * <br>2026/11/03：增加同步只读阶段通知，不在其他线程推进链。
 * <br>2026/11/07：各同步阶段及原任务入口复核停止状态。
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.0
 * @version 4.8.7-l
 */
public class TaskStageChain {
    /// 任务执行的阶段链，从 taskConfig 中获取。
    private final List<ITaskStage> stages;

    /// 要运行的任务
    @Getter
    private final ITask<?> task;

    /// 当前执行的阶段
    private int index = 0;
    private final java.util.function.Consumer<String> observer;

    /**
     * @param task 原任务
     * @param stages 原单线程阶段
     */
    protected TaskStageChain(ITask<?> task, List<ITaskStage> stages) {
        this(task, stages, ignored -> {});
    }

    /**
     * 2026/11/03：观察者不能改变链的返回值或传播观测错误。
     * @param task 原任务
     * @param stages 原同步阶段
     * @param observer 同步阶段通知
     */
    protected TaskStageChain(ITask<?> task, List<ITaskStage> stages, java.util.function.Consumer<String> observer) {
        this.stages = stages;
        this.task = task;
        this.observer = observer;
    }

    /** @param name 当前同步执行节点 */
    private void observed(String name) {
        try {
            observer.accept(name);
        } catch (RuntimeException ignored) {
            // 观测故障不参与原阶段的异常与重试策略。
        }
    }

    /**
     * 处理下一个阶段。
     * @return 返回的最终 result
     * @throws Exception 如果处理阶段出现异常。
     */
    @Nullable
    public ITaskResult<?> next() throws Exception {
        // 2026/11/06：每个阶段及原 Callable 入口复核同一任务控制。
        task.getExecution().control().checkActive();
        if (index < stages.size()) {
            var stage = stages.get(index++);
            // 2026/11/03：记录真实进入的阶段，不提前展开或执行其他阶段。
            observed("同步阶段 " + index + "/" + stages.size());
            return stage.process(this);
        } else {
            // 执行最终的任务
            observed("执行原任务方法");
            return task.getConfig().getCallable().call();
        }
    }
}
