package org.zexnocs.teanekocore.actuator.timer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.framework.lifescycle.ILivable;
import org.zexnocs.teanekocore.framework.lifescycle.IPausable;

import java.util.function.Function;

/**
 * 一个简单实现的 TaskConfig 类，用于定时器任务的配置。
 * <br>2026/11/04：允许构建时提供明确的展示声明，否则从任务来源解析。
 *
 * @param <T> 原定时任务结果类型
 * @author zExNocs
 * @date 2026/02/15
 * @since 4.0.5
 * @version 4.7.1-l
 */
@Getter
@Builder
@AllArgsConstructor
public class TimerTaskConfig<T> implements ITimerTaskConfig<T> {
    // 2026/11/04：只存不可变元数据，不由管理页面改写任务配置或推断业务来源。
    private final @Nullable TimerPresentation presentation;

    /** @return 显式构建值或原方法来源的展示声明 */
    @Override
    public @NonNull TimerPresentation getPresentation() {
        return presentation == null ? ITimerTaskConfig.super.getPresentation() : presentation;
    }

    /// 任务配置
    @NonNull
    private final ITaskConfig<T> taskConfig;

    /**
     * 2026/11/04：保留原四参数构造，展示合同从原任务解析。
     * @param taskConfig 原任务配置
     * @param taskFutureChain 原完成链
     * @param pausable 原暂停来源
     * @param livable 原生命周期
     */
    public TimerTaskConfig(ITaskConfig<T> taskConfig,
            @Nullable Function<TaskFuture<ITaskResult<T>>, TaskFuture<?>> taskFutureChain,
            @Nullable IPausable pausable, @Nullable ILivable livable) {
        this(null, taskConfig, taskFutureChain, pausable, livable);
    }

    /**
     * 用于配置 Future 执行链的函数。
     * 内部可自行设置异常处理器。
     * 该 Future 会自动在后面使用 finish() 函数，无需在该函数中调用。
     * 2026/11/04：空值表示没有额外完成链，访问器保持接口的空值合同。
     */
    @Setter
    @Builder.Default
    private @Nullable Function<TaskFuture<ITaskResult<T>>, TaskFuture<?>> taskFutureChain = null;

    /**
     * 获取暂停周期。
     * 如果为 null 则表示没有暂停周期限制，也就是不会被暂停。
     * 2026/11/04：将既有空值行为明确传递给生成的访问器。
     */
    @Setter
    @Builder.Default
    private @Nullable IPausable pausable = null;

    /**
     * 获取计时器的生命周期。
     * 如果为 null 则表示没有生命周期限制，也就是不会被删除。
     * 2026/11/04：空值继续表示没有来源生命周期，不提前终止资源。
     */
    @Setter
    @Builder.Default
    private @Nullable ILivable livable = null;
}
