package org.zexnocs.teanekocore.actuator.timer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.framework.lifescycle.ILivable;
import org.zexnocs.teanekocore.framework.lifescycle.IPausable;

import java.util.function.Function;

/**
 * 一个简单实现的 TaskConfig 类，用于定时器任务的配置。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@Getter
@Builder
@AllArgsConstructor
public class TimerTaskConfig<T> implements ITimerTaskConfig<T> {

    /// 任务配置
    @NonNull
    private final ITaskConfig<T> taskConfig;

    /**
     * 用于配置 Future 执行链的函数。
     * 内部可自行设置异常处理器。
     * 该 Future 会自动在后面使用 finish() 函数，无需在该函数中调用。
     */
    @Setter
    @Builder.Default
    private Function<TaskFuture<ITaskResult<T>>, TaskFuture<?>> taskFutureChain = null;

    /**
     * 获取暂停周期。
     * 如果为 null 则表示没有暂停周期限制，也就是不会被暂停。
     */
    @Setter
    @Builder.Default
    private IPausable pausable = null;

    /**
     * 获取计时器的生命周期。
     * 如果为 null 则表示没有生命周期限制，也就是不会被删除。
     */
    @Setter
    @Builder.Default
    private ILivable livable = null;
}
