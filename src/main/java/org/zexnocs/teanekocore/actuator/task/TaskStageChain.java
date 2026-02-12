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
 * @author zExNocs
 * @date 2026/02/11
 */
public class TaskStageChain {
    /// 任务执行的阶段链，从 taskConfig 中获取。
    private final List<ITaskStage> stages;

    /// 要运行的任务
    @Getter
    private final ITask<?> task;

    /// 当前执行的阶段
    private int index = 0;

    /// 构造函数
    protected TaskStageChain(ITask<?> task, List<ITaskStage> stages) {
        this.stages = stages;
        this.task = task;
    }

    /**
     * 处理下一个阶段。
     * @return 返回的最终 result
     */
    @Nullable
    public ITaskResult<?> next() {
        if(index < stages.size()) {
            var stage = stages.get(index++);
            return stage.process(this);
        } else {
            // 执行最终的任务
            return task.getConfig().getSupplier().get();
        }
    }
}
